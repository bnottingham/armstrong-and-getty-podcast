package com.nomnomsom.armstrongandgetty.media

import com.nomnomsom.armstrongandgetty.util.AppLog
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.AVPlayerTimeControlStatusPlaying
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.pause
import platform.AVFoundation.rate
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.AVFoundation.timeControlStatus
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.MediaPlayer.MPChangePlaybackPositionCommandEvent
import platform.MediaPlayer.MPMediaItemPropertyAlbumTitle
import platform.MediaPlayer.MPMediaItemPropertyArtist
import platform.MediaPlayer.MPMediaItemPropertyPlaybackDuration
import platform.MediaPlayer.MPMediaItemPropertyTitle
import platform.MediaPlayer.MPNowPlayingInfoCenter
import platform.MediaPlayer.MPNowPlayingInfoPropertyElapsedPlaybackTime
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackRate
import platform.MediaPlayer.MPRemoteCommandCenter
import platform.MediaPlayer.MPRemoteCommandHandlerStatusCommandFailed
import platform.MediaPlayer.MPRemoteCommandHandlerStatusSuccess
import platform.MediaPlayer.MPSkipIntervalCommandEvent

/**
 * AVPlayer-backed queue player, entirely in Kotlin/Native.
 *
 * AVQueuePlayer discards played items and can't seek backward across the queue, so this keeps
 * its own item list and swaps `AVPlayerItem`s in a single AVPlayer. Fresh AVPlayerItems are
 * created on every window switch — an ended item can be flaky when reused.
 *
 * Also owns the system integration AVPlayer doesn't do by itself: the playback audio session
 * (background audio + AirPlay), lock-screen/Control Center metadata via MPNowPlayingInfoCenter,
 * and remote commands (play/pause, ±30s skips, scrubbing) via MPRemoteCommandCenter.
 */
@OptIn(ExperimentalForeignApi::class)
class AvPlatformPlayer : PlatformPlayer {

    companion object {
        private const val TAG = "AvPlatformPlayer"
        private const val SKIP_INTERVAL_SECONDS = 30.0
    }

    private val player = AVPlayer()
    private var items: List<PlayerItem> = emptyList()
    private var currentIndex: Int = 0
    private var desiredRate: Float = 1f
    private var onStateChanged: (() -> Unit)? = null
    private var endObserver: Any? = null
    private var connected = false

    override fun connect() {
        if (connected) return
        connected = true

        configureAudioSession()
        configureRemoteCommands()

        endObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue
        ) { notification ->
            if (notification?.`object` === player.currentItem || player.currentItem == null) {
                onItemEnded()
            }
        }
    }

    override fun disconnect() {
        // Keep the session/commands registered — the app process owns exactly one player and
        // playback may continue in the background while UI-level consumers disconnect.
        endObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        endObserver = null
        connected = false
    }

    private fun configureAudioSession() {
        try {
            val session = AVAudioSession.sharedInstance()
            session.setCategory(AVAudioSessionCategoryPlayback, error = null)
            session.setActive(true, error = null)
        } catch (e: Exception) {
            AppLog.w(TAG, "Audio session setup failed", e)
        }
    }

    private fun configureRemoteCommands() {
        val center = MPRemoteCommandCenter.sharedCommandCenter()

        center.playCommand.addTargetWithHandler { _ ->
            play(); onStateChanged?.invoke()
            MPRemoteCommandHandlerStatusSuccess
        }
        center.pauseCommand.addTargetWithHandler { _ ->
            pause(); onStateChanged?.invoke()
            MPRemoteCommandHandlerStatusSuccess
        }
        center.togglePlayPauseCommand.addTargetWithHandler { _ ->
            if (isPlaying) pause() else play()
            onStateChanged?.invoke()
            MPRemoteCommandHandlerStatusSuccess
        }

        center.skipForwardCommand.preferredIntervals = listOf(SKIP_INTERVAL_SECONDS)
        center.skipForwardCommand.addTargetWithHandler { event ->
            val interval = (event as? MPSkipIntervalCommandEvent)?.interval ?: SKIP_INTERVAL_SECONDS
            seekWithinWindow(currentPositionInWindowMs + (interval * 1000).toLong())
            onStateChanged?.invoke()
            MPRemoteCommandHandlerStatusSuccess
        }
        center.skipBackwardCommand.preferredIntervals = listOf(SKIP_INTERVAL_SECONDS)
        center.skipBackwardCommand.addTargetWithHandler { event ->
            val interval = (event as? MPSkipIntervalCommandEvent)?.interval ?: SKIP_INTERVAL_SECONDS
            seekWithinWindow(currentPositionInWindowMs - (interval * 1000).toLong())
            onStateChanged?.invoke()
            MPRemoteCommandHandlerStatusSuccess
        }

        center.changePlaybackPositionCommand.addTargetWithHandler { event ->
            val positionEvent = event as? MPChangePlaybackPositionCommandEvent
                ?: return@addTargetWithHandler MPRemoteCommandHandlerStatusCommandFailed
            seekWithinWindow((positionEvent.positionTime * 1000).toLong())
            onStateChanged?.invoke()
            MPRemoteCommandHandlerStatusSuccess
        }

        // The app seeks by ±10/30s; item-to-item jumps happen via the segment list in-app.
        center.nextTrackCommand.enabled = false
        center.previousTrackCommand.enabled = false
    }

    private fun onItemEnded() {
        if (currentIndex < items.size - 1) {
            switchToWindow(currentIndex + 1, 0L, resumePlaying = true)
        } else {
            // End of playlist — stay parked at the end, paused (Media3 behavior).
            player.pause()
            updateNowPlaying()
        }
        onStateChanged?.invoke()
    }

    override fun setItems(
        items: List<PlayerItem>,
        startWindow: Int,
        startPositionMs: Long,
        autoPlay: Boolean
    ) {
        this.items = items
        if (items.isEmpty()) {
            player.replaceCurrentItemWithPlayerItem(null)
            currentIndex = 0
            updateNowPlaying()
            return
        }
        switchToWindow(startWindow.coerceIn(0, items.size - 1), startPositionMs, resumePlaying = false)
        if (autoPlay) {
            play()
        }
    }

    override fun addItems(items: List<PlayerItem>) {
        this.items = this.items + items
        updateNowPlaying()
    }

    override fun clearItemsAndStop() {
        player.pause()
        player.replaceCurrentItemWithPlayerItem(null)
        items = emptyList()
        currentIndex = 0
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = null
    }

    override fun play() {
        player.rate = desiredRate
        updateNowPlaying()
    }

    override fun pause() {
        player.pause()
        updateNowPlaying()
    }

    override fun seekTo(windowIndex: Int, positionMs: Long) {
        if (items.isEmpty()) return
        val target = windowIndex.coerceIn(0, items.size - 1)
        if (target == currentIndex && player.currentItem != null) {
            seekWithinWindow(positionMs)
        } else {
            switchToWindow(target, positionMs, resumePlaying = isPlaying)
        }
    }

    override fun setSpeed(speed: Float) {
        desiredRate = speed
        if (isPlaying) {
            player.rate = speed
        }
        updateNowPlaying()
    }

    override val itemCount: Int
        get() = items.size

    override val currentWindowIndex: Int
        get() = currentIndex

    override val currentPositionInWindowMs: Long
        get() {
            if (player.currentItem == null) return 0L
            val seconds = CMTimeGetSeconds(player.currentTime())
            if (seconds.isNaN() || seconds < 0) return 0L
            return (seconds * 1000).toLong()
        }

    override val isPlaying: Boolean
        get() = player.rate > 0f

    override val speed: Float
        get() = desiredRate

    override val isReadyOrBuffering: Boolean
        get() = player.currentItem != null && player.currentItem?.status != AVPlayerItemStatusFailed

    override fun setOnStateChanged(callback: (() -> Unit)?) {
        onStateChanged = callback
    }

    private fun switchToWindow(windowIndex: Int, positionMs: Long, resumePlaying: Boolean) {
        val item = items.getOrNull(windowIndex) ?: return
        currentIndex = windowIndex

        val url = NSURL.fileURLWithPath(item.filePath)
        val playerItem = AVPlayerItem(uRL = url)
        player.replaceCurrentItemWithPlayerItem(playerItem)
        if (positionMs > 0) {
            seekWithinWindow(positionMs)
        }
        if (resumePlaying) {
            player.rate = desiredRate
        }
        updateNowPlaying()
    }

    private fun seekWithinWindow(positionMs: Long) {
        val clamped = positionMs.coerceAtLeast(0)
        player.seekToTime(
            time = CMTimeMakeWithSeconds(clamped / 1000.0, 1000),
            toleranceBefore = CMTimeMake(0, 1),
            toleranceAfter = CMTimeMake(0, 1)
        )
        updateNowPlaying()
    }

    private fun updateNowPlaying() {
        val item = items.getOrNull(currentIndex)
        if (item == null) {
            MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = null
            return
        }

        val durationSeconds = player.currentItem?.let { CMTimeGetSeconds(it.duration) } ?: 0.0
        val info = mutableMapOf<Any?, Any?>(
            MPMediaItemPropertyTitle to item.title,
            MPMediaItemPropertyArtist to item.artist,
            MPMediaItemPropertyAlbumTitle to item.albumTitle,
            MPNowPlayingInfoPropertyElapsedPlaybackTime to currentPositionInWindowMs / 1000.0,
            MPNowPlayingInfoPropertyPlaybackRate to (if (isPlaying) desiredRate.toDouble() else 0.0)
        )
        if (!durationSeconds.isNaN() && durationSeconds > 0) {
            info[MPMediaItemPropertyPlaybackDuration] = durationSeconds
        }
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = info
    }
}
