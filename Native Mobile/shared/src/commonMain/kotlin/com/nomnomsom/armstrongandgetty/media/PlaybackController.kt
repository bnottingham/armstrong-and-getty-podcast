package com.nomnomsom.armstrongandgetty.media

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Positions/durations here are "virtual" — measured across the concatenated
 * playlist of segments, not within a single queue item.
 */
data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1f,
    val currentDayDate: String? = null,
    val currentSegmentIndex: Int = 0,
    val isReady: Boolean = false
)

class PlaybackController(
    private val player: PlatformPlayer
) {
    companion object {
        /** Key used on Android MediaMetadata extras to carry the remote (streaming) URL
         *  so Cast can reach the audio when the local item uri is a `file://` path. */
        const val EXTRA_REMOTE_URL = "remote_url"

        /** Companion key carrying the downloaded file's path, so the service can swap
         *  back to local playback when a Cast session ends. */
        const val EXTRA_LOCAL_PATH = "local_path"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var positionUpdater: kotlinx.coroutines.Job? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var currentDayDate: String? = null

    private var segmentDurations: List<Long> = emptyList()
    /** Cumulative start times: `segmentStartMs[i]` = sum of durations `[0..i-1]`. */
    private var segmentStartMs: List<Long> = emptyList()
    /** Maps player window index back to the source segment index for partial/live playlists. */
    private var playlistSegmentIndices: List<Int> = emptyList()

    fun connect() {
        player.setOnStateChanged { updateState() }
        player.connect()
        startPositionUpdater()
    }

    fun disconnect() {
        positionUpdater?.cancel()
        positionUpdater = null
        player.setOnStateChanged(null)
        player.disconnect()
    }

    /**
     * Play a day's podcast as a playlist of segment files.
     * `remoteUrls` rides along on each item and is used when switching to Cast on
     * Android, which can't stream file:// URIs.
     */
    fun playPlaylist(
        dayDate: String,
        title: String,
        segmentFilePaths: List<String>,
        segmentTitles: List<String>,
        remoteUrls: List<String>,
        segmentIndices: List<Int>,
        actualDurations: List<Long>,
        startPositionMs: Long = 0L,
        autoPlay: Boolean = true
    ) {
        currentDayDate = dayDate
        segmentDurations = segmentFilePaths.indices.map { actualDurations.getOrElse(it) { 0L } }
        segmentStartMs = cumulativeStarts(segmentDurations)
        playlistSegmentIndices = segmentFilePaths.indices.map { segmentIndices.getOrElse(it) { it } }

        val items = segmentFilePaths.mapIndexed { index, path ->
            PlayerItem(
                filePath = path,
                remoteUrl = remoteUrls.getOrElse(index) { "" },
                title = "$title — ${segmentTitles.getOrElse(index) { "Segment ${index + 1}" }}",
                artist = "Armstrong & Getty",
                albumTitle = "Armstrong & Getty On Demand",
                trackNumber = index + 1
            )
        }

        val (startWindow, posInWindow) = if (startPositionMs > 0 && segmentStartMs.isNotEmpty()) {
            virtualPosToSegmentPos(startPositionMs)
        } else {
            0 to 0L
        }

        player.setItems(items, startWindow, posInWindow, autoPlay)
        updateState()
    }

    /** Append newly-downloaded segments for the currently-playing day without interrupting playback. */
    fun appendToPlaylist(
        dayDate: String,
        dayTitle: String,
        newSegmentFilePaths: List<String>,
        newSegmentTitles: List<String>,
        newRemoteUrls: List<String>,
        newSegmentIndices: List<Int>,
        newActualDurations: List<Long>
    ) {
        if (currentDayDate != dayDate) return
        val appendCount = minOf(
            newSegmentFilePaths.size,
            newSegmentIndices.size,
            newActualDurations.size
        )
        if (appendCount <= 0) return

        segmentDurations = segmentDurations + newActualDurations.take(appendCount)
        segmentStartMs = cumulativeStarts(segmentDurations)
        playlistSegmentIndices = playlistSegmentIndices + newSegmentIndices.take(appendCount)

        val existingCount = player.itemCount
        val items = newSegmentFilePaths.take(appendCount).mapIndexed { i, path ->
            PlayerItem(
                filePath = path,
                remoteUrl = newRemoteUrls.getOrElse(i) { "" },
                title = "$dayTitle — ${newSegmentTitles.getOrElse(i) { "Segment ${existingCount + i + 1}" }}",
                artist = "Armstrong & Getty",
                albumTitle = "Armstrong & Getty On Demand",
                trackNumber = existingCount + i + 1
            )
        }

        player.addItems(items)
        updateState()
    }

    private fun cumulativeStarts(durations: List<Long>): List<Long> = buildList {
        var cumulative = 0L
        for (dur in durations) {
            add(cumulative)
            cumulative += dur
        }
    }

    fun loadedSegmentIndices(): List<Int> = playlistSegmentIndices

    fun pause() {
        player.pause()
    }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
        updateState()
    }

    /**
     * Stop playback, drop the current playlist, and reset this controller's per-day state.
     * Used when the user deletes the currently-playing episode — after this, [playbackState]
     * reports no active day so the "Now Playing" UI disappears.
     */
    fun stopAndClear() {
        player.clearItemsAndStop()
        currentDayDate = null
        segmentDurations = emptyList()
        segmentStartMs = emptyList()
        playlistSegmentIndices = emptyList()
        updateState()
    }

    fun seekTo(virtualPositionMs: Long) {
        val (segIndex, posInSeg) = virtualPosToSegmentPos(virtualPositionMs)
        player.seekTo(segIndex, posInSeg)
        updateState()
    }

    fun seekToSegment(segmentIndex: Int) {
        val windowIndex = playlistSegmentIndices.indexOf(segmentIndex)
        if (windowIndex in 0 until player.itemCount) {
            player.seekTo(windowIndex, 0)
            updateState()
        }
    }

    fun seekRelative(deltaMs: Long) {
        val currentVirtual = computeVirtualPosition()
        val totalDuration = segmentDurations.sum()
        val newPos = (currentVirtual + deltaMs).coerceIn(0, totalDuration)
        seekTo(newPos)
    }

    fun setPlaybackSpeed(speed: Float) {
        player.setSpeed(speed)
        updateState()
    }

    fun cyclePlaybackSpeed(): Float {
        val speeds = listOf(1f, 1.25f, 1.5f, 2f)
        val current = player.speed
        val currentIdx = speeds.indexOfFirst { abs(it - current) < 0.01f }
        val nextSpeed = speeds[(currentIdx + 1) % speeds.size]
        setPlaybackSpeed(nextSpeed)
        return nextSpeed
    }

    fun computeVirtualPosition(): Long {
        val segIndex = player.currentWindowIndex
        val posInSeg = player.currentPositionInWindowMs.coerceAtLeast(0)
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

    private fun updateState() {
        val segIndex = player.currentWindowIndex
        val totalDuration = segmentDurations.sum()
        val originalSegmentIndex = playlistSegmentIndices.getOrElse(segIndex) { segIndex }

        _playbackState.value = PlaybackState(
            isPlaying = player.isPlaying,
            currentPositionMs = computeVirtualPosition(),
            durationMs = totalDuration,
            playbackSpeed = player.speed,
            currentDayDate = currentDayDate,
            currentSegmentIndex = originalSegmentIndex,
            isReady = player.isReadyOrBuffering
        )
    }

    private fun startPositionUpdater() {
        positionUpdater?.cancel()
        positionUpdater = scope.launch {
            while (isActive) {
                updateState()
                delay(500)
            }
        }
    }
}
