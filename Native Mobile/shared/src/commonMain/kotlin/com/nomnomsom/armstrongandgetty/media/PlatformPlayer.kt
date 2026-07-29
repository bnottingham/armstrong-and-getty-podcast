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
    val speed: Float
    val isReadyOrBuffering: Boolean

    /** Invoked on meaningful player state changes (play/pause, item transitions). Polling in
     *  [PlaybackController] covers position ticks; this exists so state flips render instantly. */
    fun setOnStateChanged(callback: (() -> Unit)?)
}
