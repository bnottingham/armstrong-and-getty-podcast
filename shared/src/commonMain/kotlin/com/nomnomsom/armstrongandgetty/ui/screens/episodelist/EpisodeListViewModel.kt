package com.nomnomsom.armstrongandgetty.ui.screens.episodelist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

private data class PlayableSegment(
    val originalIndex: Int,
    val segment: Segment,
    val filePath: String
)

class EpisodeListViewModel(
    private val repository: PodcastRepository,
    val playbackController: PlaybackController
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

                val latestDay = repository.getDayByDate(latestDate)
                if (latestDay != null && !repository.isUserDeleted(latestDate)) {
                    when (latestDay.state) {
                        DownloadState.NONE -> autoDownload(latestDate)
                        DownloadState.DOWNLOADED -> {
                            val segments = repository.parseSegments(latestDay.segmentsJson)
                            when {
                                !latestDay.isComplete -> checkForNewSegments(latestDate)
                                // A complete day can still be missing files if OMT was added after initial download.
                                !repository.hasAllSegmentsOnDisk(latestDate, segments.size) -> autoDownload(latestDate)
                            }
                        }
                        DownloadState.ERROR -> autoDownload(latestDate)
                        DownloadState.DOWNLOADING -> Unit
                    }
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    isRefreshing = false,
                    error = result.exceptionOrNull()?.message
                )
            }
        }
    }

    /** Manual download (user tapped the download button). Clears any prior user deletion. */
    fun downloadDay(date: String) {
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
            repository.downloadDay(date) { progress ->
                _downloadProgress.value = _downloadProgress.value + (date to progress)
            }
        } finally {
            _downloadProgress.value = _downloadProgress.value - date
        }
    }

    /** Cancel an in-flight day download. Whatever segments finished stay on disk. */
    fun cancelDownload(date: String) {
        viewModelScope.launch { repository.cancelDownload(date) }
        downloadJobs[date]?.cancel()
    }

    /** Cancel a single in-flight segment without killing the rest of the download. */
    fun cancelSegment(date: String, index: Int) {
        viewModelScope.launch { repository.cancelSegment(date, index) }
    }

    fun retrySegment(date: String, index: Int) {
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
        viewModelScope.launch {
            repository.resetProgress(date)
        }
    }

    fun deleteDay(date: String) {
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

    fun togglePlayPause() = playbackController.togglePlayPause()
    fun seekRelative(deltaMs: Long) = playbackController.seekRelative(deltaMs)
    fun seekTo(positionMs: Long) = playbackController.seekTo(positionMs)
    fun seekToSegment(index: Int) = playbackController.seekToSegment(index)
    fun cycleSpeed(): Float = playbackController.cyclePlaybackSpeed()

    fun getSegmentsForDay(day: PodcastDay): List<Segment> {
        return repository.parseSegments(day.segmentsJson)
    }

    fun saveListenProgress() {
        val state = playbackState.value
        val date = state.currentDayDate ?: return
        if (state.durationMs <= 0) return
        // Don't overwrite real progress with 0 during player state transitions.
        if (state.currentPositionMs <= 0) return

        val isListened = state.currentPositionMs >= state.durationMs - 5000

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
