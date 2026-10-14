package com.nomnomsom.armstrongandgetty.media

/** One entry in the playback queue. */
data class PlayerItem(
    val filePath: String,
    /** Original streaming URL — used by Cast on Android, which can't read file:// URIs. */
    val remoteUrl: String,
    val title: String,
    val artist: String,
    val albumTitle: String,
    val trackNumber: Int
)

/**
 * Narrow platform audio interface the common [PlaybackController] drives.
 *
 * Android: a Media3 MediaController bound to the app's MediaLibraryService (keeps the
 * notification, Android Auto, and Chromecast behavior). iOS: AVPlayer + MPNowPlayingInfoCenter
 * + MPRemoteCommandCenter, written in Kotlin/Native.
 *
 * Window indices are queue positions (0-based); positions are within the current window.
 */
interface PlatformPlayer {
    fun connect()
    fun disconnect()

    fun setItems(items: List<PlayerItem>, startWindow: Int, startPositionMs: Long, autoPlay: Boolean)
    fun addItems(items: List<PlayerItem>)
    fun clearItemsAndStop()

    fun play()
    fun pause()
    fun seekTo(windowIndex: Int, positionMs: Long)
    fun setSpeed(speed: Float)

    val itemCount: Int
    val currentWindowIndex: Int
    val currentPositionInWindowMs: Long
    val isPlaying: Boolean

    /**
     * The listener's intent to play: true from a play command until a pause command (or
     * stop), and it stays true while the system holds audio back (buffering, an audio-focus
     * loss, a call or Siri). Media3 `playWhenReady`; iOS tracks it around play()/pause().
     */
    val playWhenReady: Boolean get() = isPlaying

    /** The queue played through to its natural end (Media3 STATE_ENDED / last AVPlayerItem ended). */
    val hasEnded: Boolean get() = false

    val speed: Float
    val isReadyOrBuffering: Boolean

    /** Invoked on meaningful player state changes (play/pause, item transitions). Polling in
     *  [PlaybackController] covers position ticks; this exists so state flips render instantly. */
    fun setOnStateChanged(callback: (() -> Unit)?)
}
