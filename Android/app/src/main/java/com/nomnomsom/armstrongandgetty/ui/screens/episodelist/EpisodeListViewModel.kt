package com.nomnomsom.armstrongandgetty.ui.screens.episodelist

import android.util.Log
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class EpisodeListUiState(
    val days: List<PodcastDay> = emptyList(),
    val isRefreshing: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class EpisodeListViewModel @Inject constructor(
    private val repository: PodcastRepository,
    val playbackController: PlaybackController
) : ViewModel() {

    companion object {
        private const val TAG = "EpisodeListVM"
    }

    private val _uiState = MutableStateFlow(EpisodeListUiState())
    val uiState: StateFlow<EpisodeListUiState> = _uiState.asStateFlow()

    val playbackState: StateFlow<PlaybackState> = playbackController.playbackState

    private val _downloadProgress = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, DownloadProgress>> = _downloadProgress.asStateFlow()

    // One Job per in-flight day download so the user can cancel from the UI.
    private val downloadJobs = mutableMapOf<String, Job>()

    init {
        viewModelScope.launch {
            repository.observeAllDays().collect { days ->
                _uiState.value = _uiState.value.copy(days = days)
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
        repository.cancelDownload(date)
        downloadJobs[date]?.cancel()
    }

    /** Cancel a single in-flight segment without killing the rest of the download. */
    fun cancelSegment(date: String, index: Int) {
        repository.cancelSegment(date, index)
    }

    fun retrySegment(date: String, index: Int) {
        viewModelScope.launch {
            val result = repository.retrySegment(date, index) { progress ->
                _downloadProgress.value = _downloadProgress.value + (date to progress)
            }
            if (result.isFailure) {
                Log.w(TAG, "Segment $index retry failed for $date: ${result.exceptionOrNull()?.message}")
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
            Log.d(TAG, "deleteDay $date complete — state reset to NONE")
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
        val segments = repository.parseSegments(day.segmentsJson)
        if (segments.isEmpty()) return false
        val allFilePaths = repository.getSegmentFilePaths(day.date, segments.size)

        val inFlight = _downloadProgress.value[day.date]?.segmentsInProgress ?: emptySet()
        val paired = segments.zip(allFilePaths).filterIndexed { index, (_, path) ->
            // Currently-downloading files have non-zero size but partial bytes — exclude them so
            // we don't hand ExoPlayer a half-written mp3.
            index !in inFlight && java.io.File(path).exists() && java.io.File(path).length() > 0
        }
        if (paired.isEmpty()) return false
        val (playableSegments, filePaths) = paired.unzip()

        playbackController.playPlaylist(
            dayDate = day.date,
            title = day.title,
            segmentFilePaths = filePaths,
            segmentTitles = playableSegments.map { it.displayLabel },
            remoteUrls = playableSegments.map { it.audioUrl },
            actualDurations = playableSegments.map { it.effectiveDurationMs },
            startPositionMs = startPositionMs,
            autoPlay = autoPlay
        )
        return true
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
        viewModelScope.launch {
            val dayBefore = repository.getDayByDate(date) ?: return@launch
            val previousSegmentCount = repository.parseSegments(dayBefore.segmentsJson).size

            val result = repository.appendNewSegments(date)

            if (result.isSuccess && playbackState.value.currentDayDate == date) {
                val updatedDay = repository.getDayByDate(date) ?: return@launch
                val updatedSegments = repository.parseSegments(updatedDay.segmentsJson)

                if (updatedSegments.size > previousSegmentCount) {
                    val newSegments = updatedSegments.subList(previousSegmentCount, updatedSegments.size)
                    val allFilePaths = repository.getSegmentFilePaths(date, updatedSegments.size)
                    val newFilePaths = allFilePaths.subList(previousSegmentCount, allFilePaths.size)

                    playbackController.appendToPlaylist(
                        dayTitle = updatedDay.title,
                        newSegmentFilePaths = newFilePaths,
                        newSegmentTitles = newSegments.map { it.displayLabel },
                        newRemoteUrls = newSegments.map { it.audioUrl },
                        newActualDurations = newSegments.map { it.effectiveDurationMs }
                    )

                    Log.d(TAG, "Appended ${newSegments.size} new segment(s) to live playlist for $date")
                }
            }
        }
    }

    override fun onCleared() {
        saveListenProgress()
        playbackController.disconnect()
        super.onCleared()
    }
}
