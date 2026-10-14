package com.nomnomsom.armstrongandgetty.ui.screens.episodelist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nomnomsom.armstrongandgetty.analytics.AnalyticsEvents
import com.nomnomsom.armstrongandgetty.analytics.AnalyticsTracker
import com.nomnomsom.armstrongandgetty.data.model.DownloadProgress
import com.nomnomsom.armstrongandgetty.data.model.DownloadState
import com.nomnomsom.armstrongandgetty.data.model.PodcastDay
import com.nomnomsom.armstrongandgetty.data.model.Segment
import com.nomnomsom.armstrongandgetty.data.model.displayLabel
import com.nomnomsom.armstrongandgetty.data.model.effectiveDurationMs
import com.nomnomsom.armstrongandgetty.data.model.state
import com.nomnomsom.armstrongandgetty.data.repository.PodcastRepository
import com.nomnomsom.armstrongandgetty.download.DownloadPolicy
import com.nomnomsom.armstrongandgetty.download.DownloadSettings
import com.nomnomsom.armstrongandgetty.download.NetworkKind
import com.nomnomsom.armstrongandgetty.download.WifiRequiredException
import com.nomnomsom.armstrongandgetty.download.autoDownloadCandidates
import com.nomnomsom.armstrongandgetty.media.PlaybackController
import com.nomnomsom.armstrongandgetty.media.PlaybackState
import com.nomnomsom.armstrongandgetty.review.SessionHealth
import com.nomnomsom.armstrongandgetty.util.AppLog
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM

data class EpisodeListUiState(
    val days: List<PodcastDay> = emptyList(),
    val isRefreshing: Boolean = false,
    val error: String? = null
)

/**
 * Whether playback position counts as "finished the episode". Being within 5s of the
 * end of the LOADED playlist is not enough: at the live frontier the loaded playlist
 * ends long before the episode does (hour 2 isn't out yet), and marking the day
 * listened there makes the next play restart from zero and wipe progress. The day must
 * also be complete, with every known segment loaded.
 */
internal fun shouldMarkListened(
    positionMs: Long,
    durationMs: Long,
    dayIsComplete: Boolean,
    daySegmentCount: Int,
    loadedSegmentCount: Int
): Boolean {
    if (durationMs <= 0) return false
    val nearEnd = positionMs >= durationMs - 5000
    return nearEnd && dayIsComplete && loadedSegmentCount >= daySegmentCount
}

/**
 * Whether the Episodes list is showing something the listener has to deal with, so the
 * in-app rating prompt waits: a pull-to-refresh in progress, a "Download failed" row
 * (persisted, so possibly from an earlier run), or segments that failed on this pass.
 */
internal fun hasVisibleReviewBlocker(
    state: EpisodeListUiState,
    downloadProgress: Map<String, DownloadProgress>
): Boolean =
    state.isRefreshing ||
        state.days.any { it.state == DownloadState.ERROR } ||
        downloadProgress.values.any { it.segmentsFailed.isNotEmpty() }

/**
 * A manual Download / Retry tap that "Download over Wi-Fi only" held back. Drives
 * [WifiRequiredDialog]. [segmentIndex] is null for a whole-day download; [network] is the
 * reading that blocked it; [partial] = some hours landed before Wi-Fi went away and the rest
 * are waiting.
 */
data class WifiRequiredPrompt(
    val date: String,
    val segmentIndex: Int? = null,
    val network: NetworkKind = NetworkKind.METERED,
    val partial: Boolean = false,
) {
    /**
     * "Turn Off & Download" helps only on a metered connection. Offline (or with no reading)
     * the download would just fail after the listener gave up their data-saving choice.
     */
    val offersTurnOff: Boolean get() = network == NetworkKind.METERED
}

private data class PlayableSegment(
    val originalIndex: Int,
    val segment: Segment,
    val filePath: String
)

class EpisodeListViewModel(
    private val repository: PodcastRepository,
    val playbackController: PlaybackController,
    private val analytics: AnalyticsTracker,
    // Feed / download failures hold the in-app rating prompt until a success (or 30 min).
    private val sessionHealth: SessionHealth,
    // "Download over Wi-Fi only" (default off). Manual taps pre-check it and get a dialog when
    // blocked; automatic paths stay silent. A block is never a failure.
    private val downloadPolicy: DownloadPolicy,
    private val downloadSettings: DownloadSettings
) : ViewModel() {

    companion object {
        private const val TAG = "EpisodeListVM"
    }

    private val fs = FileSystem.SYSTEM

    private val _uiState = MutableStateFlow(EpisodeListUiState())
    val uiState: StateFlow<EpisodeListUiState> = _uiState.asStateFlow()

    val playbackState: StateFlow<PlaybackState> = playbackController.playbackState

    private val _downloadProgress = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, DownloadProgress>> = _downloadProgress.asStateFlow()

    private val _wifiRequiredPrompt = MutableStateFlow<WifiRequiredPrompt?>(null)

    /** Non-null while the "Wi-Fi Only Is On" dialog should show for a blocked manual tap. */
    val wifiRequiredPrompt: StateFlow<WifiRequiredPrompt?> = _wifiRequiredPrompt.asStateFlow()

    /** [hasVisibleReviewBlocker], live; changes only when the answer does. */
    val reviewBlocker: StateFlow<Boolean> =
        combine(_uiState, _downloadProgress, ::hasVisibleReviewBlocker)
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // One Job per in-flight day download so the user can cancel from the UI.
    private val downloadJobs = mutableMapOf<String, Job>()
    private val liveSegmentCheckJobs = mutableMapOf<String, Job>()

    // Latest day from the last successful refresh, for the Wi-Fi-only catch-up pass.
    private var lastLatestDate: String? = null

    init {
        viewModelScope.launch {
            repository.observeAllDays().collect { days ->
                _uiState.value = _uiState.value.copy(days = days)
                val currentDate = playbackState.value.currentDayDate
                if (currentDate != null) {
                    days.find { it.date == currentDate }?.let { appendPlayableSegmentsToCurrentPlaylist(it) }
                }
            }
        }

        // "Download over Wi-Fi only" catch-up: when downloads become allowed again (Wi-Fi is
        // back, or the setting was turned off on mobile data), rerun the auto-download pass.
        // With the setting off allowedFlow is a single `true`, which drop(1) skips — so this
        // never fires and never reads the network.
        viewModelScope.launch {
            downloadPolicy.allowedFlow.drop(1).filter { it }.collect {
                lastLatestDate?.let { runAutoDownloadPass(it) }
            }
        }

        playbackController.connect()
        refreshFeed()
    }

    fun refreshFeed() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true, error = null)
            val result = repository.refreshFeed()
            if (result.isSuccess) {
                sessionHealth.reportSuccess("feed_refresh")
                val latestDate = result.getOrThrow()
                lastLatestDate = latestDate
                _uiState.value = _uiState.value.copy(isRefreshing = false)
                runAutoDownloadPass(latestDate)
            } else {
                sessionHealth.reportFailure("feed_refresh")
                analytics.logEvent(AnalyticsEvents.FEED_REFRESH_FAILED)
                analytics.recordError("Feed refresh failed", result.exceptionOrNull())
                _uiState.value = _uiState.value.copy(
                    isRefreshing = false,
                    error = result.exceptionOrNull()?.message
                )
            }
        }
    }

    /**
     * Automatic downloads after a refresh (and, with Wi-Fi only on, when Wi-Fi comes back).
     * Manages today's day as well as the newest one (see [autoDownloadCandidates]).
     */
    private suspend fun runAutoDownloadPass(latestDate: String) {
        for (date in autoDownloadCandidates(latestDate, repository.todayKey())) {
            val day = repository.getDayByDate(date) ?: continue
            if (repository.isUserDeleted(date)) continue
            when (day.state) {
                DownloadState.NONE -> autoDownload(date)
                DownloadState.DOWNLOADED -> {
                    val segments = repository.parseSegments(day.segmentsJson)
                    when {
                        !day.isComplete -> checkForNewSegments(date)
                        // A complete day can still be missing files if OMT was added after initial download.
                        !repository.hasAllSegmentsOnDisk(date, segments.size) -> autoDownload(date)
                    }
                }
                DownloadState.ERROR -> autoDownload(date)
                DownloadState.DOWNLOADING -> Unit
            }
        }
    }

    /** Manual download (user tapped the download button). Clears any prior user deletion. */
    fun downloadDay(date: String) {
        analytics.logEvent(AnalyticsEvents.EPISODE_DOWNLOAD, mapOf(AnalyticsEvents.PARAM_DATE to date))
        launchDownload(date) {
            // Remembered so a block caused by turning the setting ON mid-download stays silent.
            val wifiOnlyAtTap = downloadSettings.wifiOnly.value
            // Checked before the deletion mark or any state changes: a blocked tap changes nothing.
            downloadPolicy.blockedBy()?.let { network ->
                promptWifi(WifiRequiredPrompt(date, network = network))
                return@launchDownload
            }
            repository.clearUserDeletion(date)
            performDownload(date, manual = true, wifiOnlyAtTap = wifiOnlyAtTap)
        }
    }

    private fun autoDownload(date: String) {
        launchDownload(date) { performDownload(date, manual = false, wifiOnlyAtTap = false) }
    }

    private fun launchDownload(date: String, block: suspend () -> Unit) {
        // If a download for this date is already running, leave it alone — we don't want a second
        // launch to clobber the progress callback or race on segment files.
        if (downloadJobs[date]?.isActive == true) return
        val job = viewModelScope.launch { block() }
        downloadJobs[date] = job
        job.invokeOnCompletion { downloadJobs.remove(date) }
    }

    private suspend fun performDownload(date: String, manual: Boolean, wifiOnlyAtTap: Boolean) {
        try {
            val result = repository.downloadDay(date) { progress ->
                _downloadProgress.value = _downloadProgress.value + (date to progress)
            }
            val error = result.exceptionOrNull()
            when {
                result.isSuccess -> {
                    sessionHealth.reportSuccess("download")
                    // Some hours landed, then Wi-Fi went away: say the rest is waiting rather
                    // than calling it complete. Setting off at tap → never checked (default path).
                    val restBlockedBy = if (manual && wifiOnlyAtTap) restOfDayBlockedBy(date) else null
                    if (restBlockedBy != null) {
                        promptWifi(WifiRequiredPrompt(date, network = restBlockedBy, partial = true))
                    } else {
                        analytics.logEvent(AnalyticsEvents.DOWNLOAD_COMPLETED, mapOf(AnalyticsEvents.PARAM_DATE to date))
                    }
                }
                // Held back by "Download over Wi-Fi only": blocked, not failed. No SessionHealth
                // failure, no download_failed, no Crashlytics — the day stays NONE.
                error is WifiRequiredException -> when {
                    !manual -> logWaitingForWifi(date, source = "auto")
                    // The listener turned the setting on while this ran: their choice, no dialog.
                    !wifiOnlyAtTap -> logWaitingForWifi(date, source = "setting_changed")
                    // Wi-Fi went away between the tap's check and the first transfer.
                    else -> promptWifi(
                        WifiRequiredPrompt(date, network = downloadPolicy.blockedBy() ?: NetworkKind.UNKNOWN)
                    )
                }
                else -> {
                    sessionHealth.reportFailure("download")
                    analytics.logEvent(AnalyticsEvents.DOWNLOAD_FAILED, mapOf(AnalyticsEvents.PARAM_DATE to date))
                    analytics.recordError("Episode download failed", error)
                }
            }
        } finally {
            _downloadProgress.value = _downloadProgress.value - date
        }
    }

    /** The reading blocking downloads when [date] still has hours missing; null otherwise. */
    private suspend fun restOfDayBlockedBy(date: String): NetworkKind? {
        val network = downloadPolicy.blockedBy() ?: return null
        val day = repository.getDayByDate(date) ?: return null
        return if (repository.missingSegmentIndices(date, day.segmentCount).isNotEmpty()) network else null
    }

    /** Cancel an in-flight day download. Whatever segments finished stay on disk. */
    fun cancelDownload(date: String) {
        analytics.logEvent(AnalyticsEvents.DOWNLOAD_CANCELLED, mapOf(AnalyticsEvents.PARAM_DATE to date))
        viewModelScope.launch { repository.cancelDownload(date) }
        downloadJobs[date]?.cancel()
    }

    /** Cancel a single in-flight segment without killing the rest of the download. */
    fun cancelSegment(date: String, index: Int) {
        viewModelScope.launch { repository.cancelSegment(date, index) }
    }

    fun retrySegment(date: String, index: Int) {
        analytics.logEvent(
            AnalyticsEvents.SEGMENT_RETRY,
            mapOf(AnalyticsEvents.PARAM_DATE to date, AnalyticsEvents.PARAM_SEGMENT_INDEX to index)
        )
        viewModelScope.launch {
            val wifiOnlyAtTap = downloadSettings.wifiOnly.value
            downloadPolicy.blockedBy()?.let { network ->
                promptWifi(WifiRequiredPrompt(date, segmentIndex = index, network = network))
                return@launch
            }
            val result = repository.retrySegment(date, index) { progress ->
                _downloadProgress.value = _downloadProgress.value + (date to progress)
            }
            val error = result.exceptionOrNull()
            if (error is WifiRequiredException) {
                // Blocked, not failed: Wi-Fi dropped between the check and the transfer, or the
                // listener turned the setting on meanwhile (their choice — no dialog).
                if (wifiOnlyAtTap) {
                    promptWifi(
                        WifiRequiredPrompt(
                            date,
                            segmentIndex = index,
                            network = downloadPolicy.blockedBy() ?: NetworkKind.UNKNOWN
                        )
                    )
                } else {
                    logWaitingForWifi(date, source = "setting_changed")
                }
            } else if (result.isFailure) {
                sessionHealth.reportFailure("segment_retry")
                AppLog.w(TAG, "Segment $index retry failed for $date: ${error?.message}")
            } else {
                sessionHealth.reportSuccess("segment_retry")
                repository.getDayByDate(date)?.let { appendPlayableSegmentsToCurrentPlaylist(it) }
            }
            // Clear in-flight state for this retry; the row will refresh from disk check.
            val current = _downloadProgress.value[date]
            if (current != null && current.segmentsInProgress.isEmpty() && current.segmentsFailed.isEmpty()) {
                _downloadProgress.value = _downloadProgress.value - date
            }
        }
    }

    /** A manual tap was held back: log it and show the dialog (set last). */
    private fun promptWifi(prompt: WifiRequiredPrompt) {
        logWaitingForWifi(prompt.date, source = "manual")
        _wifiRequiredPrompt.value = prompt
    }

    private fun logWaitingForWifi(date: String, source: String) {
        analytics.logEvent(
            AnalyticsEvents.DOWNLOAD_WAITING_WIFI,
            mapOf(AnalyticsEvents.PARAM_DATE to date, AnalyticsEvents.PARAM_SOURCE to source)
        )
    }

    /**
     * Dialog's "Turn Off & Download": turn the setting off, then redo the tap that was held back
     * (for a partial download, fetch the rest). Offered only on a metered connection.
     */
    fun turnOffWifiOnlyAndRetry() {
        val prompt = _wifiRequiredPrompt.value ?: return
        _wifiRequiredPrompt.value = null
        if (!prompt.offersTurnOff) return
        downloadSettings.setWifiOnly(false)
        analytics.logEvent(
            AnalyticsEvents.DOWNLOAD_WIFI_ONLY_SET,
            mapOf(AnalyticsEvents.PARAM_ENABLED to "false", AnalyticsEvents.PARAM_SOURCE to "dialog")
        )
        viewModelScope.launch {
            // The blocked job may still be finishing; launchDownload would ignore a second launch.
            downloadJobs[prompt.date]?.join()
            val segmentIndex = prompt.segmentIndex
            if (segmentIndex == null) downloadDay(prompt.date) else retrySegment(prompt.date, segmentIndex)
        }
    }

    /** Dialog's "Not Now" (or dismissed). */
    fun dismissWifiRequiredPrompt() {
        _wifiRequiredPrompt.value = null
    }

    fun missingSegmentIndices(day: PodcastDay): Set<Int> =
        repository.missingSegmentIndices(day.date, day.segmentCount)

    fun resetProgress(date: String) {
        analytics.logEvent(AnalyticsEvents.EPISODE_RESTART, mapOf(AnalyticsEvents.PARAM_DATE to date))
        viewModelScope.launch {
            repository.resetProgress(date)
        }
    }

    fun deleteDay(date: String) {
        analytics.logEvent(AnalyticsEvents.EPISODE_DELETE, mapOf(AnalyticsEvents.PARAM_DATE to date))
        viewModelScope.launch {
            if (playbackState.value.currentDayDate == date) {
                playbackController.stopAndClear()
            }
            repository.deleteDay(date)
            _downloadProgress.value = _downloadProgress.value - date
            AppLog.d(TAG, "deleteDay $date complete — state reset to NONE")
        }
    }

    fun loadDay(day: PodcastDay) {
        if (playbackState.value.currentDayDate == day.date) return
        preparePlaylist(day, startPositionMs = day.listenedPositionMs, autoPlay = false)
    }

    fun playDay(day: PodcastDay) {
        analytics.logEvent(AnalyticsEvents.EPISODE_PLAY, mapOf(AnalyticsEvents.PARAM_DATE to day.date))
        val startPos = if (day.isListened) 0L else day.listenedPositionMs
        val prepared = preparePlaylist(day, startPositionMs = startPos, autoPlay = true)
        if (prepared && day.isListened) {
            viewModelScope.launch {
                repository.resetProgress(day.date)
            }
        }
    }

    private fun preparePlaylist(
        day: PodcastDay,
        startPositionMs: Long,
        autoPlay: Boolean
    ): Boolean {
        // No state gate — a partial download (state can be DOWNLOADING / ERROR / NONE if the user
        // retried individual segments) is still playable as long as some segment files exist.
        // Files that are mid-write or missing are filtered below.
        val playableSegments = playableSegmentsFor(day)
        if (playableSegments.isEmpty()) return false

        playbackController.playPlaylist(
            dayDate = day.date,
            title = day.title,
            segmentFilePaths = playableSegments.map { it.filePath },
            segmentTitles = playableSegments.map { it.segment.displayLabel },
            remoteUrls = playableSegments.map { it.segment.audioUrl },
            segmentIndices = playableSegments.map { it.originalIndex },
            actualDurations = playableSegments.map { it.segment.effectiveDurationMs },
            startPositionMs = startPositionMs,
            autoPlay = autoPlay
        )
        return true
    }

    private fun playableSegmentsFor(day: PodcastDay): List<PlayableSegment> {
        val segments = repository.parseSegments(day.segmentsJson)
        if (segments.isEmpty()) return emptyList()

        val inFlight = _downloadProgress.value[day.date]?.segmentsInProgress ?: emptySet()
        val filePaths = repository.getSegmentFilePaths(day.date, segments.size)

        return segments.mapIndexedNotNull { index, segment ->
            val path = filePaths.getOrNull(index) ?: return@mapIndexedNotNull null
            val metadata = fs.metadataOrNull(path.toPath())
            // Currently-downloading files may exist as .part files; final files are only promoted
            // after a complete download, but the in-flight check also protects older installs.
            if (index !in inFlight && metadata?.isRegularFile == true && (metadata.size ?: 0L) > 0) {
                PlayableSegment(index, segment, path)
            } else {
                null
            }
        }
    }

    private fun appendPlayableSegmentsToCurrentPlaylist(day: PodcastDay) {
        if (playbackState.value.currentDayDate != day.date) return

        val loadedIndices = playbackController.loadedSegmentIndices()
        val maxLoadedIndex = loadedIndices.maxOrNull() ?: return
        val segmentsToAppend = playableSegmentsFor(day)
            .filter { it.originalIndex > maxLoadedIndex && it.originalIndex !in loadedIndices }
            .sortedBy { it.originalIndex }

        if (segmentsToAppend.isEmpty()) return

        playbackController.appendToPlaylist(
            dayDate = day.date,
            dayTitle = day.title,
            newSegmentFilePaths = segmentsToAppend.map { it.filePath },
            newSegmentTitles = segmentsToAppend.map { it.segment.displayLabel },
            newRemoteUrls = segmentsToAppend.map { it.segment.audioUrl },
            newSegmentIndices = segmentsToAppend.map { it.originalIndex },
            newActualDurations = segmentsToAppend.map { it.segment.effectiveDurationMs }
        )

        AppLog.d(TAG, "Appended ${segmentsToAppend.size} playable segment(s) to live playlist for ${day.date}")
    }

    fun togglePlayPause() {
        val wasPlaying = playbackState.value.isPlaying
        analytics.logEvent(
            if (wasPlaying) AnalyticsEvents.EPISODE_PAUSE else AnalyticsEvents.EPISODE_PLAY,
            mapOf(AnalyticsEvents.PARAM_DATE to (playbackState.value.currentDayDate ?: ""))
        )
        playbackController.togglePlayPause()
    }

    fun seekRelative(deltaMs: Long) {
        analytics.logEvent(AnalyticsEvents.SEEK, mapOf(AnalyticsEvents.PARAM_DELTA_MS to deltaMs))
        playbackController.seekRelative(deltaMs)
    }

    fun seekTo(positionMs: Long) {
        analytics.logEvent(AnalyticsEvents.SEEK)
        playbackController.seekTo(positionMs)
    }

    fun seekToSegment(index: Int) {
        analytics.logEvent(AnalyticsEvents.SEGMENT_JUMP, mapOf(AnalyticsEvents.PARAM_SEGMENT_INDEX to index))
        playbackController.seekToSegment(index)
    }

    fun cycleSpeed(): Float {
        val newSpeed = playbackController.cyclePlaybackSpeed()
        analytics.logEvent(AnalyticsEvents.SPEED_CHANGE, mapOf(AnalyticsEvents.PARAM_SPEED to newSpeed.toString()))
        return newSpeed
    }

    fun getSegmentsForDay(day: PodcastDay): List<Segment> {
        return repository.parseSegments(day.segmentsJson)
    }

    fun saveListenProgress() {
        val state = playbackState.value
        val date = state.currentDayDate ?: return
        if (state.durationMs <= 0) return
        // Don't overwrite real progress with 0 during player state transitions.
        if (state.currentPositionMs <= 0) return

        val day = _uiState.value.days.find { it.date == date }
        val isListened = if (day == null) {
            state.currentPositionMs >= state.durationMs - 5000
        } else {
            shouldMarkListened(
                positionMs = state.currentPositionMs,
                durationMs = state.durationMs,
                dayIsComplete = day.isComplete,
                daySegmentCount = day.segmentCount,
                loadedSegmentCount = playbackController.loadedSegmentIndices().size
            )
        }

        // NonCancellable so the DB write completes during onCleared() when viewModelScope is cancelling.
        viewModelScope.launch {
            withContext(NonCancellable) {
                repository.updateListenProgress(date, state.currentPositionMs, isListened)
            }
        }
    }

    fun checkForNewSegments(date: String) {
        if (downloadJobs[date]?.isActive == true || liveSegmentCheckJobs[date]?.isActive == true) return

        val job = viewModelScope.launch {
            try {
                val result = repository.appendNewSegments(date) { progress ->
                    _downloadProgress.value = _downloadProgress.value + (date to progress)
                }
                val error = result.exceptionOrNull()
                if (error is WifiRequiredException) {
                    // New hours are recorded; their files wait for Wi-Fi. Not a failure.
                    AppLog.d(TAG, "Live segment check for $date: new hours wait for Wi-Fi")
                } else if (result.isFailure) {
                    AppLog.w(TAG, "Live segment check failed for $date: ${error?.message}")
                }

                val updatedDay = repository.getDayByDate(date) ?: return@launch
                appendPlayableSegmentsToCurrentPlaylist(updatedDay)
            } finally {
                _downloadProgress.value = _downloadProgress.value - date
            }
        }
        liveSegmentCheckJobs[date] = job
        job.invokeOnCompletion { liveSegmentCheckJobs.remove(date) }
    }

    override fun onCleared() {
        saveListenProgress()
        playbackController.disconnect()
        super.onCleared()
    }
}
