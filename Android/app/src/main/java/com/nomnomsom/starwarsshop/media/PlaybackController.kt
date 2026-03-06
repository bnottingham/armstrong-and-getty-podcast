package com.nomnomsom.starwarsshop.media

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L, // Virtual position across all segments
    val durationMs: Long = 0L, // Total duration across all segments
    val playbackSpeed: Float = 1f,
    val currentDayDate: String? = null,
    val currentSegmentIndex: Int = 0, // Which segment is currently playing
    val positionInSegmentMs: Long = 0L, // Position within the current segment
    val isReady: Boolean = false
)

@Singleton
class PlaybackController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var currentDayDate: String? = null

    // Actual measured durations for each segment in the current playlist
    private var segmentDurations: List<Long> = emptyList()
    // Cumulative start times: segmentStartMs[i] = sum of durations[0..i-1]
    private var segmentStartMs: List<Long> = emptyList()

    fun connect() {
        if (controllerFuture != null) return

        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture!!.addListener(
            {
                controller = controllerFuture!!.get()
                setupPlayerListener()
                startPositionUpdater()
            },
            MoreExecutors.directExecutor()
        )
    }

    fun disconnect() {
        controller = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
    }

    /**
     * Play a day's podcast as a playlist of individual segment files.
     *
     * @param dayDate The date key
     * @param title The display title
     * @param segmentFilePaths List of file paths, one per segment
     * @param segmentTitles List of titles for each segment
     * @param actualDurations Measured durations for each segment in ms
     * @param startPositionMs Virtual start position across all segments
     */
    fun playPlaylist(
        dayDate: String,
        title: String,
        segmentFilePaths: List<String>,
        segmentTitles: List<String>,
        actualDurations: List<Long>,
        startPositionMs: Long = 0L,
        autoPlay: Boolean = true
    ) {
        val ctrl = controller ?: return
        currentDayDate = dayDate
        segmentDurations = actualDurations

        // Compute cumulative start times
        segmentStartMs = buildList {
            var cumulative = 0L
            for (dur in actualDurations) {
                add(cumulative)
                cumulative += dur
            }
        }

        // Build media items for each segment
        val mediaItems = segmentFilePaths.mapIndexed { index, path ->
            val segTitle = segmentTitles.getOrElse(index) { "Segment ${index + 1}" }
            MediaItem.Builder()
                .setUri(Uri.parse("file://$path"))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("$title — $segTitle")
                        .setArtist("Armstrong & Getty")
                        .setAlbumTitle("Armstrong & Getty On Demand")
                        .setTrackNumber(index + 1)
                        .build()
                )
                .build()
        }

        ctrl.setMediaItems(mediaItems)
        ctrl.prepare()

        // Seek to the correct segment and position within it
        if (startPositionMs > 0 && segmentStartMs.isNotEmpty()) {
            val (segIndex, posInSeg) = virtualPosToSegmentPos(startPositionMs)
            ctrl.seekTo(segIndex, posInSeg)
        } else {
            ctrl.seekTo(0, 0)
        }

        if (autoPlay) {
            ctrl.play()
        }
    }

    fun resume() {
        controller?.play()
    }

    fun pause() {
        controller?.pause()
    }

    fun togglePlayPause() {
        val ctrl = controller ?: return
        if (ctrl.isPlaying) ctrl.pause() else ctrl.play()
    }

    /**
     * Seek to a virtual position across all segments.
     */
    fun seekTo(virtualPositionMs: Long) {
        val ctrl = controller ?: return
        val (segIndex, posInSeg) = virtualPosToSegmentPos(virtualPositionMs)
        ctrl.seekTo(segIndex, posInSeg)
    }

    /**
     * Seek to the start of a specific segment.
     */
    fun seekToSegment(segmentIndex: Int) {
        val ctrl = controller ?: return
        if (segmentIndex in 0 until ctrl.mediaItemCount) {
            ctrl.seekTo(segmentIndex, 0)
        }
    }

    fun seekRelative(deltaMs: Long) {
        val currentVirtual = computeVirtualPosition()
        val totalDuration = segmentDurations.sum()
        val newPos = (currentVirtual + deltaMs).coerceIn(0, totalDuration)
        seekTo(newPos)
    }

    fun setPlaybackSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
    }

    fun cyclePlaybackSpeed(): Float {
        val speeds = listOf(1f, 1.25f, 1.5f, 2f)
        val current = controller?.playbackParameters?.speed ?: 1f
        val currentIdx = speeds.indexOfFirst { kotlin.math.abs(it - current) < 0.01f }
        val nextSpeed = speeds[(currentIdx + 1) % speeds.size]
        setPlaybackSpeed(nextSpeed)
        return nextSpeed
    }

    /**
     * Get the current virtual position across all segments.
     */
    fun computeVirtualPosition(): Long {
        val ctrl = controller ?: return 0L
        val segIndex = ctrl.currentMediaItemIndex
        val posInSeg = ctrl.currentPosition.coerceAtLeast(0)
        return if (segIndex in segmentStartMs.indices) {
            segmentStartMs[segIndex] + posInSeg
        } else {
            posInSeg
        }
    }

    private fun virtualPosToSegmentPos(virtualMs: Long): Pair<Int, Long> {
        if (segmentStartMs.isEmpty()) return Pair(0, virtualMs)

        var segIndex = 0
        for (i in segmentStartMs.indices) {
            if (virtualMs >= segmentStartMs[i]) segIndex = i
        }
        val posInSeg = (virtualMs - segmentStartMs[segIndex]).coerceAtLeast(0)
        return Pair(segIndex, posInSeg)
    }

    private fun setupPlayerListener() {
        controller?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateState()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                updateState()
            }

            override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
                updateState()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateState()
            }
        })
        updateState()
    }

    private fun updateState() {
        val ctrl = controller ?: return
        val segIndex = ctrl.currentMediaItemIndex
        val posInSeg = ctrl.currentPosition.coerceAtLeast(0)
        val virtualPos = if (segIndex in segmentStartMs.indices) {
            segmentStartMs[segIndex] + posInSeg
        } else {
            posInSeg
        }
        val totalDuration = segmentDurations.sum().takeIf { it > 0 } ?: ctrl.duration.coerceAtLeast(0)

        _playbackState.value = PlaybackState(
            isPlaying = ctrl.isPlaying,
            currentPositionMs = virtualPos,
            durationMs = totalDuration,
            playbackSpeed = ctrl.playbackParameters.speed,
            currentDayDate = currentDayDate,
            currentSegmentIndex = segIndex,
            positionInSegmentMs = posInSeg,
            isReady = ctrl.playbackState == Player.STATE_READY || ctrl.playbackState == Player.STATE_BUFFERING
        )
    }

    private fun startPositionUpdater() {
        scope.launch {
            while (isActive) {
                updateState()
                delay(500)
            }
        }
    }
}