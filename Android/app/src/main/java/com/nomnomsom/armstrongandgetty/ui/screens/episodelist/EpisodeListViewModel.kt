package com.nomnomsom.armstrongandgetty.ui.screens.episodelist

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nomnomsom.armstrongandgetty.data.model.DownloadState
import com.nomnomsom.armstrongandgetty.data.model.PodcastDay
import com.nomnomsom.armstrongandgetty.data.model.Segment
import com.nomnomsom.armstrongandgetty.data.repository.PodcastRepository
import com.nomnomsom.armstrongandgetty.media.PlaybackController
import com.nomnomsom.armstrongandgetty.media.PlaybackState
import dagger.hilt.android.lifecycle.HiltViewModel
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

data class DownloadProgress(
    val currentSegment: Int,       // 0-based index of the segment being downloaded
    val totalSegments: Int,        // total segment count for the day
    val segmentBytesDownloaded: Long,
    val segmentTotalBytes: Long    // -1 if unknown
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
                if (latestDay != null) {
                    when {
                        latestDay.downloadState == DownloadState.NONE.value -> downloadDay(latestDate)
                        latestDay.downloadState == DownloadState.DOWNLOADED.value -> {
                            val segments = repository.parseSegments(latestDay.segmentsJson)
                            when {
                                // Day still in progress — check for new segments (e.g. live show)
                                !latestDay.isComplete -> checkForNewSegments(latestDate)
                                // Day complete but not all files on disk (e.g. OMT added after initial download)
                                !repository.hasAllSegmentsOnDisk(latestDate, segments.size) -> downloadDay(latestDate)
                            }
                        }
                        latestDay.downloadState == DownloadState.ERROR.value -> downloadDay(latestDate)
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

    fun downloadDay(date: String) {
        viewModelScope.launch {
            repository.downloadDay(date) { segmentIndex, segmentCount, bytesDownloaded, totalBytes ->
                _downloadProgress.value = _downloadProgress.value + (date to DownloadProgress(
                    currentSegment = segmentIndex,
                    totalSegments = segmentCount,
                    segmentBytesDownloaded = bytesDownloaded,
                    segmentTotalBytes = totalBytes
                ))
            }
            // Clear progress when download completes (success or failure)
            _downloadProgress.value = _downloadProgress.value - date
        }
    }

    fun resetProgress(date: String) {
        viewModelScope.launch {
            repository.resetProgress(date)
        }
    }

    fun deleteDay(date: String) {
        viewModelScope.launch {
            if (playbackState.value.currentDayDate == date) {
                playbackController.pause()
            }
            repository.deleteDay(date)
        }
    }

    fun loadDay(day: PodcastDay) {
        if (day.downloadState != DownloadState.DOWNLOADED.value) return
        if (playbackState.value.currentDayDate == day.date) return

        val segments = repository.parseSegments(day.segmentsJson)
        val allFilePaths = repository.getSegmentFilePaths(day.date, segments.size)

        // Only include segments whose files exist on disk (OMT may not be downloaded yet)
        val paired = segments.zip(allFilePaths).filter { (_, path) -> java.io.File(path).exists() }
        if (paired.isEmpty()) return
        val (playableSegments, filePaths) = paired.unzip()

        val segTitles = playableSegments.map { seg ->
            if (seg.hour == "OMT") "OMT: ${seg.title}" else "Hr ${seg.hour}: ${seg.title}"
        }
        val remoteUrls = playableSegments.map { it.audioUrl }
        val actualDurations = playableSegments.map { seg ->
            if (seg.actualDurationMs > 0) seg.actualDurationMs else seg.durationMs
        }

        playbackController.playPlaylist(
            dayDate = day.date,
            title = day.title,
            segmentFilePaths = filePaths,
            segmentTitles = segTitles,
            remoteUrls = remoteUrls,
            actualDurations = actualDurations,
            startPositionMs = day.listenedPositionMs,
            autoPlay = false
        )
    }

    fun playDay(day: PodcastDay) {
        if (day.downloadState != DownloadState.DOWNLOADED.value) return

        val segments = repository.parseSegments(day.segmentsJson)
        val allFilePaths = repository.getSegmentFilePaths(day.date, segments.size)

        // Only include segments whose files exist on disk (OMT may not be downloaded yet)
        val paired = segments.zip(allFilePaths).filter { (_, path) -> java.io.File(path).exists() }
        if (paired.isEmpty()) return
        val (playableSegments, filePaths) = paired.unzip()

        val segTitles = playableSegments.map { seg ->
            if (seg.hour == "OMT") "OMT: ${seg.title}" else "Hr ${seg.hour}: ${seg.title}"
        }
        val remoteUrls = playableSegments.map { it.audioUrl }
        val actualDurations = playableSegments.map { seg ->
            if (seg.actualDurationMs > 0) seg.actualDurationMs else seg.durationMs
        }

        playbackController.playPlaylist(
            dayDate = day.date,
            title = day.title,
            segmentFilePaths = filePaths,
            segmentTitles = segTitles,
            remoteUrls = remoteUrls,
            actualDurations = actualDurations,
            startPositionMs = if (day.isListened) 0L else day.listenedPositionMs
        )

        if (day.isListened) {
            viewModelScope.launch {
                repository.resetProgress(day.date)
            }
        }
    }

    fun togglePlayPause() = playbackController.togglePlayPause()
    fun seekRelative(deltaMs: Long) = playbackController.seekRelative(deltaMs)
    fun seekTo(positionMs: Long) = playbackController.seekTo(positionMs)
    fun seekToSegment(index: Int) = playbackController.seekToSegment(index)
    fun cycleSpeed(): Float = playbackController.cyclePlaybackSpeed()

    fun getSegmentsForDay(day: PodcastDay): List<Segment> {
        return repository.parseSegments(day.segmentsJson)
    }

    // ── Progress persistence (local only) ───────────────

    fun saveListenProgress() {
        val state = playbackState.value
        val date = state.currentDayDate ?: return
        if (state.durationMs <= 0) return
        // Don't overwrite real progress with 0 during player state transitions
        if (state.currentPositionMs <= 0) return

        val isListened = state.currentPositionMs >= state.durationMs - 5000

        // Use NonCancellable so the DB write completes even during scope cancellation
        // (e.g. when onCleared() fires and viewModelScope is about to be cancelled)
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
                    val newTitles = newSegments.map { seg ->
                        if (seg.hour == "OMT") "OMT: ${seg.title}" else "Hr ${seg.hour}: ${seg.title}"
                    }
                    val newRemoteUrls = newSegments.map { it.audioUrl }
                    val newDurations = newSegments.map { seg ->
                        if (seg.actualDurationMs > 0) seg.actualDurationMs else seg.durationMs
                    }

                    playbackController.appendToPlaylist(
                        dayTitle = updatedDay.title,
                        newSegmentFilePaths = newFilePaths,
                        newSegmentTitles = newTitles,
                        newRemoteUrls = newRemoteUrls,
                        newActualDurations = newDurations
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
