package com.nomnomsom.armstrongandgetty.ui.screens.episodelist

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.nomnomsom.armstrongandgetty.data.model.DownloadState
import com.nomnomsom.armstrongandgetty.data.model.PodcastDay
import com.nomnomsom.armstrongandgetty.data.model.Segment
import com.nomnomsom.armstrongandgetty.data.model.TimedWord
import com.nomnomsom.armstrongandgetty.data.model.TranscriptEntity
import com.nomnomsom.armstrongandgetty.data.model.TranscriptState
import com.nomnomsom.armstrongandgetty.data.remote.ProgressSyncRepository
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
    private val voskModelManager: VoskModelManager,
    private val progressSyncRepository: ProgressSyncRepository,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    companion object {
        private const val TAG = "EpisodeListVM"
    }

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

        // If signed in, pull remote progress and merge with local
        syncRemoteProgressToLocal()
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
                        latestDay.downloadState == DownloadState.NONE.value -> {
                            downloadDay(latestDate)
                        }
                        latestDay.downloadState == DownloadState.DOWNLOADED.value && !latestDay.isComplete -> {
                            checkForNewSegments(latestDate)
                        }
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
            // Also reset remote progress if signed in
            progressSyncRepository.pushProgress(date, 0L, false)
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

    fun observeTranscriptsForDay(date: String, segmentCount: Int) {
        viewModelScope.launch {
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

    /**
     * Save listen progress to Room (always) and Firestore (if signed in).
     */
    fun saveListenProgress() {
        val state = playbackState.value
        val date = state.currentDayDate ?: return
        if (state.durationMs <= 0) return

        val isListened = state.currentPositionMs >= state.durationMs - 5000

        viewModelScope.launch {
            // Always save locally
            repository.updateListenProgress(date, state.currentPositionMs, isListened)

            // Also sync to Firestore if the user is authenticated
            progressSyncRepository.pushProgress(date, state.currentPositionMs, isListened)
        }
    }

    /**
     * On startup (if signed in), pull all remote progress and merge with local.
     * Remote wins if its lastUpdated timestamp is newer than the local lastUpdated.
     */
    private fun syncRemoteProgressToLocal() {
        if (firebaseAuth.currentUser == null) return

        viewModelScope.launch {
            try {
                val remoteProgress = progressSyncRepository.pullAllProgress()
                if (remoteProgress.isEmpty()) return@launch

                for ((date, remote) in remoteProgress) {
                    val localDay = repository.getDayByDate(date) ?: continue

                    // Remote wins if it has a newer timestamp than local
                    if (remote.lastUpdated > localDay.lastUpdated) {
                        Log.d(TAG, "Applying remote progress for $date: ${remote.listenedPositionMs}ms")
                        repository.updateListenProgress(
                            date,
                            remote.listenedPositionMs,
                            remote.isListened
                        )
                    } else {
                        // Local is newer — push local to remote so they stay in sync
                        if (localDay.listenedPositionMs > 0) {
                            progressSyncRepository.pushProgress(
                                date,
                                localDay.listenedPositionMs,
                                localDay.isListened
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync remote progress", e)
            }
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
