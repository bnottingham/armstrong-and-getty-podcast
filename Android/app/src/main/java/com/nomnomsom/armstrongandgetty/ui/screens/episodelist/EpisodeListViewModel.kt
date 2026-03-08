package com.nomnomsom.armstrongandgetty.ui.screens.episodelist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nomnomsom.armstrongandgetty.data.model.DownloadState
import com.nomnomsom.armstrongandgetty.data.model.PodcastDay
import com.nomnomsom.armstrongandgetty.data.model.Segment
import com.nomnomsom.armstrongandgetty.data.model.TimedWord
import com.nomnomsom.armstrongandgetty.data.model.TranscriptEntity
import com.nomnomsom.armstrongandgetty.data.model.TranscriptState
import com.nomnomsom.armstrongandgetty.data.repository.PodcastRepository
import com.nomnomsom.armstrongandgetty.media.PlaybackController
import com.nomnomsom.armstrongandgetty.media.PlaybackState
import com.nomnomsom.armstrongandgetty.transcription.ModelStatus
import com.nomnomsom.armstrongandgetty.transcription.TranscriptionManager
import com.nomnomsom.armstrongandgetty.transcription.VoskModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EpisodeListUiState(
    val days: List<PodcastDay> = emptyList(),
    val isRefreshing: Boolean = false,
    val error: String? = null
)

data class TranscriptUiState(
    val transcripts: List<TranscriptEntity> = emptyList(),
    val isTranscribing: Boolean = false,
    val modelStatus: ModelStatus = ModelStatus()
)

@HiltViewModel
class EpisodeListViewModel @Inject constructor(
    private val repository: PodcastRepository,
    val playbackController: PlaybackController,
    private val transcriptionManager: TranscriptionManager,
    private val voskModelManager: VoskModelManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(EpisodeListUiState())
    val uiState: StateFlow<EpisodeListUiState> = _uiState.asStateFlow()

    val playbackState: StateFlow<PlaybackState> = playbackController.playbackState

    private val _transcriptState = MutableStateFlow(TranscriptUiState())
    val transcriptState: StateFlow<TranscriptUiState> = _transcriptState.asStateFlow()

    val modelStatus: StateFlow<ModelStatus> = voskModelManager.status

    init {
        // Observe all days from DB
        viewModelScope.launch {
            repository.observeAllDays().collect { days ->
                _uiState.value = _uiState.value.copy(days = days)
            }
        }

        // Connect to playback service
        playbackController.connect()

        // Initial feed refresh
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
                        // Not downloaded yet — start downloading
                        latestDay.downloadState == DownloadState.NONE.value -> {
                            downloadDay(latestDate)
                        }
                        // Already downloaded but day isn't complete — check for new segments
                        latestDay.downloadState == DownloadState.DOWNLOADED.value && !latestDay.isComplete -> {
                            checkForNewSegments(latestDate)
                        }
                        // Failed previously — retry
                        latestDay.downloadState == DownloadState.ERROR.value -> {
                            downloadDay(latestDate)
                        }
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
            repository.downloadDay(date)
        }
    }

    fun resetProgress(date: String) {
        viewModelScope.launch {
            repository.resetProgress(date)
        }
    }

    fun loadDay(day: PodcastDay) {
        if (day.downloadState != DownloadState.DOWNLOADED.value) return
        if (playbackState.value.currentDayDate == day.date) return

        val segments = repository.parseSegments(day.segmentsJson)
        val filePaths = repository.getSegmentFilePaths(day.date, segments.size)
        val segTitles = segments.map { seg ->
            if (seg.hour == "OMT") "OMT: ${seg.title}" else "Hr ${seg.hour}: ${seg.title}"
        }
        val actualDurations = segments.map { seg ->
            if (seg.actualDurationMs > 0) seg.actualDurationMs else seg.durationMs
        }

        playbackController.playPlaylist(
            dayDate = day.date,
            title = day.title,
            segmentFilePaths = filePaths,
            segmentTitles = segTitles,
            actualDurations = actualDurations,
            startPositionMs = if (day.isListened) 0L else day.listenedPositionMs,
            autoPlay = false
        )
    }

    fun playDay(day: PodcastDay) {
        if (day.downloadState != DownloadState.DOWNLOADED.value) return

        val segments = repository.parseSegments(day.segmentsJson)
        val filePaths = repository.getSegmentFilePaths(day.date, segments.size)
        val segTitles = segments.map { seg ->
            if (seg.hour == "OMT") "OMT: ${seg.title}" else "Hr ${seg.hour}: ${seg.title}"
        }
        val actualDurations = segments.map { seg ->
            if (seg.actualDurationMs > 0) seg.actualDurationMs else seg.durationMs
        }

        playbackController.playPlaylist(
            dayDate = day.date,
            title = day.title,
            segmentFilePaths = filePaths,
            segmentTitles = segTitles,
            actualDurations = actualDurations,
            startPositionMs = if (day.isListened) 0L else day.listenedPositionMs
        )

        if (day.isListened) {
            viewModelScope.launch {
                repository.resetProgress(day.date)
            }
        }
    }

    fun togglePlayPause() {
        playbackController.togglePlayPause()
    }

    fun seekRelative(deltaMs: Long) {
        playbackController.seekRelative(deltaMs)
    }

    fun seekTo(positionMs: Long) {
        playbackController.seekTo(positionMs)
    }

    fun seekToSegment(index: Int) {
        playbackController.seekToSegment(index)
    }

    fun cycleSpeed(): Float {
        return playbackController.cyclePlaybackSpeed()
    }

    fun getSegmentsForDay(day: PodcastDay): List<Segment> {
        return repository.parseSegments(day.segmentsJson)
    }

    // ── Transcription ────────────────────────────────────

    /**
     * Start observing transcripts for a day.
     * Resets any stuck 'transcribing' records from previous sessions.
     */
    fun observeTranscriptsForDay(date: String, segmentCount: Int) {
        viewModelScope.launch {
            // Reset any stuck TRANSCRIBING records from a prior crash/session
            transcriptionManager.resetStuckTranscripts(date)

            transcriptionManager.observeTranscripts(date).collect { transcripts ->
                val isTranscribing = transcripts.any { it.state == TranscriptState.TRANSCRIBING.value }
                _transcriptState.value = TranscriptUiState(
                    transcripts = transcripts,
                    isTranscribing = isTranscribing,
                    modelStatus = voskModelManager.status.value
                )
            }
        }
    }

    fun transcribeDay(date: String, segmentCount: Int) {
        viewModelScope.launch {
            _transcriptState.value = _transcriptState.value.copy(isTranscribing = true)
            transcriptionManager.transcribeDay(date, segmentCount)
        }
    }

    fun transcribeSegment(date: String, segmentIndex: Int) {
        viewModelScope.launch {
            _transcriptState.value = _transcriptState.value.copy(isTranscribing = true)
            transcriptionManager.transcribeSegment(date, segmentIndex)
        }
    }

    fun parseTimedWords(wordsJson: String): List<TimedWord> {
        return transcriptionManager.parseWords(wordsJson)
    }

    // ── Progress persistence ─────────────────────────────

    fun saveListenProgress() {
        val state = playbackState.value
        val date = state.currentDayDate ?: return
        if (state.durationMs <= 0) return

        viewModelScope.launch {
            val isListened = state.currentPositionMs >= state.durationMs - 5000
            repository.updateListenProgress(date, state.currentPositionMs, isListened)
        }
    }

    fun checkForNewSegments(date: String) {
        viewModelScope.launch {
            repository.appendNewSegments(date)
        }
    }

    override fun onCleared() {
        saveListenProgress()
        playbackController.disconnect()
        super.onCleared()
    }
}
