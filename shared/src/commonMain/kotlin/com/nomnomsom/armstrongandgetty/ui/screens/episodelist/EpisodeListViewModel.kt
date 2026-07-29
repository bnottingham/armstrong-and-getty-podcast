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
import com.nomnomsom.armstrongandgetty.media.PlaybackController
import com.nomnomsom.armstrongandgetty.media.PlaybackState
import com.nomnomsom.armstrongandgetty.util.AppLog
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

private data class PlayableSegment(
    val originalIndex: Int,
    val segment: Segment,
    val filePath: String
)

class EpisodeListViewModel(
    private val repository: PodcastRepository,
    val playbackController: PlaybackController,
    private val analytics: AnalyticsTracker
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

    // One Job per in-flight day download so the user can cancel from the UI.
    private val downloadJobs = mutableMapOf<String, Job>()
    private val liveSegmentCheckJobs = mutableMapOf<String, Job>()

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

        playbackController.connect()
        refreshFeed()
    }

    fun refreshFeed() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true, error = null)
            val result = repository.refreshFeed()
            if (result.isSuccess) {
                val latestDate = result.getOrThrow()
                _uiState.value = _uiState.value.copy(isRefreshing = false)

                // Manage today's day as well as the newest one: late-evening interviews
                // carry pubDates past UTC midnight and form a later-dated card, which
                // would otherwise steal "latest" and orphan the day still being aired.
                val todayKey = repository.todayKey()
                val candidateDates =
                    if (todayKey != latestDate) listOf(latestDate, todayKey) else listOf(latestDate)

                for (date in candidateDates) {
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
            } else {
                analytics.logEvent(AnalyticsEvents.FEED_REFRESH_FAILED)
                analytics.recordError("Feed refresh failed", result.exceptionOrNull())
                _uiState.value = _uiState.value.copy(
                    isRefreshing = false,
                    error = result.exceptionOrNull()?.message
                )
            }
        }
    }

    /** Manual download (user tapped the download button). Clears any prior user deletion. */
    fun downloadDay(date: String) {
        analytics.logEvent(AnalyticsEvents.EPISODE_DOWNLOAD, mapOf(AnalyticsEvents.PARAM_DATE to date))
        launchDownload(date) {
            repository.clearUserDeletion(date)
            performDownload(date)
        }
    }

    private fun autoDownload(date: String) {
        launchDownload(date) { performDownload(date) }
    }

    private fun launchDownload(date: String, block: suspend () -> Unit) {
        // If a download for this date is already running, leave it alone — we don't want a second
        // launch to clobber the progress callback or race on segment files.
        if (downloadJobs[date]?.isActive == true) return
        val job = viewModelScope.launch { block() }
        downloadJobs[date] = job
        job.invokeOnCompletion { downloadJobs.remove(date) }
    }

    private suspend fun performDownload(date: String) {
        try {
            val result = repository.downloadDay(date) { progress ->
                _downloadProgress.value = _downloadProgress.value + (date to progress)
            }
            if (result.isSuccess) {
                analytics.logEvent(AnalyticsEvents.DOWNLOAD_COMPLETED, mapOf(AnalyticsEvents.PARAM_DATE to date))
            } else {
                analytics.logEvent(AnalyticsEvents.DOWNLOAD_FAILED, mapOf(AnalyticsEvents.PARAM_DATE to date))
                analytics.recordError("Episode download failed", result.exceptionOrNull())
            }
        } finally {
            _downloadProgress.value = _downloadProgress.value - date
        }
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
            val result = repository.retrySegment(date, index) { progress ->
                _downloadProgress.value = _downloadProgress.value + (date to progress)
            }
            if (result.isFailure) {
                AppLog.w(TAG, "Segment $index retry failed for $date: ${result.exceptionOrNull()?.message}")
            } else {
                repository.getDayByDate(date)?.let { appendPlayableSegmentsToCurrentPlaylist(it) }
            }
            // Clear in-flight state for this retry; the row will refresh from disk check.
            val current = _downloadProgress.value[date]
            if (current != null && current.segmentsInProgress.isEmpty() && current.segmentsFailed.isEmpty()) {
                _downloadProgress.value = _downloadProgress.value - date
            }
        }
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
                if (result.isFailure) {
                    AppLog.w(TAG, "Live segment check failed for $date: ${result.exceptionOrNull()?.message}")
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
