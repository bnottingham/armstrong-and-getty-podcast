package com.nomnomsom.armstrongandgetty.media

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

/**
 * Common-playback bridge to the app's Media3 MediaLibraryService. The service itself lives in
 * the androidApp module (it owns notification drawables and the manifest entry), so it's
 * referenced by class name rather than a compile-time dependency.
 */
class Media3PlatformPlayer(
    private val context: Context
) : PlatformPlayer {

    companion object {
        private const val SERVICE_CLASS = "com.nomnomsom.armstrongandgetty.media.PlaybackService"
    }

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var onStateChanged: (() -> Unit)? = null

    override fun connect() {
        if (controllerFuture != null) return

        val sessionToken = SessionToken(
            context,
            ComponentName(context, SERVICE_CLASS)
        )
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture!!.addListener(
            {
                controller = controllerFuture!!.get()
                setupPlayerListener()
                onStateChanged?.invoke()
            },
            MoreExecutors.directExecutor()
        )
    }

    override fun disconnect() {
        controller = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
    }

    override fun setItems(
        items: List<PlayerItem>,
        startWindow: Int,
        startPositionMs: Long,
        autoPlay: Boolean
    ) {
        val ctrl = controller ?: return
        ctrl.setMediaItems(items.map { it.toMediaItem() })
        ctrl.prepare()
        ctrl.seekTo(startWindow, startPositionMs)
        if (autoPlay) {
            ctrl.play()
        }
    }

    override fun addItems(items: List<PlayerItem>) {
        controller?.addMediaItems(items.map { it.toMediaItem() })
    }

    override fun clearItemsAndStop() {
        val ctrl = controller ?: return
        ctrl.stop()
        ctrl.clearMediaItems()
    }

    override fun play() {
        controller?.play()
    }

    override fun pause() {
        controller?.pause()
    }

    override fun seekTo(windowIndex: Int, positionMs: Long) {
        controller?.seekTo(windowIndex, positionMs)
    }

    override fun setSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
    }

    override val itemCount: Int
        get() = controller?.mediaItemCount ?: 0

    override val currentWindowIndex: Int
        get() = controller?.currentMediaItemIndex ?: 0

    override val currentPositionInWindowMs: Long
        get() = controller?.currentPosition?.coerceAtLeast(0) ?: 0L

    override val isPlaying: Boolean
        get() = controller?.isPlaying ?: false

    override val speed: Float
        get() = controller?.playbackParameters?.speed ?: 1f

    override val isReadyOrBuffering: Boolean
        get() = controller?.playbackState.let {
            it == Player.STATE_READY || it == Player.STATE_BUFFERING
        }

    override fun setOnStateChanged(callback: (() -> Unit)?) {
        onStateChanged = callback
    }

    private fun setupPlayerListener() {
        controller?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                onStateChanged?.invoke()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                onStateChanged?.invoke()
            }

            override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
                onStateChanged?.invoke()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                onStateChanged?.invoke()
            }
        })
    }

    private fun PlayerItem.toMediaItem(): MediaItem {
        val extras = Bundle().apply { putString(PlaybackController.EXTRA_REMOTE_URL, remoteUrl) }
        return MediaItem.Builder()
            .setUri(Uri.parse("file://$filePath"))
            .setMimeType(MimeTypes.AUDIO_MPEG)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(albumTitle)
                    .setTrackNumber(trackNumber)
                    .setExtras(extras)
                    .build()
            )
            .build()
    }
}
