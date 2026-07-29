package com.nomnomsom.armstrongandgetty.media

import com.nomnomsom.armstrongandgetty.util.AppLog
import googlecast.GCKCastContext
import googlecast.GCKCastSession
import googlecast.GCKMediaInformationBuilder
import googlecast.GCKMediaMetadata
import googlecast.GCKMediaMetadataTypeMusicTrack
import googlecast.GCKMediaPlayerStateBuffering
import googlecast.GCKMediaPlayerStatePlaying
import googlecast.GCKMediaQueueItem
import googlecast.GCKMediaQueueItemBuilder
import googlecast.GCKMediaQueueLoadOptions
import googlecast.GCKMediaSeekOptions
import googlecast.GCKMediaStatus
import googlecast.GCKMediaStreamTypeBuffered
import googlecast.GCKRemoteMediaClient
import googlecast.GCKRemoteMediaClientListenerProtocol
import googlecast.GCKSession
import googlecast.GCKSessionManager
import googlecast.GCKSessionManagerListenerProtocol
import googlecast.kGCKMetadataKeyAlbumTitle
import googlecast.kGCKMetadataKeyArtist
import googlecast.kGCKMetadataKeyTitle
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.darwin.NSObject

/**
 * iOS counterpart of the Android Cast handoff in PlaybackService.switchToPlayer:
 * wraps the local AVPlayer and, while a cast session is active, routes every command
 * to the cast receiver instead. On session start the current playlist is re-loaded on
 * the receiver from the segments' remote URLs at the current position (a receiver can't
 * read local files); on session end local playback resumes where casting left off.
 */
@OptIn(ExperimentalForeignApi::class)
class CastAwarePlatformPlayer(
    private val local: AvPlatformPlayer
) : PlatformPlayer {

    companion object {
        private const val TAG = "CastAwarePlayer"
    }

    private var items: List<PlayerItem> = emptyList()
    private var casting = false
    private var remoteIndex = 0
    private var remoteWasPlaying = false
    private var onStateChanged: (() -> Unit)? = null
    private var listenersRegistered = false

    private val remoteClient: GCKRemoteMediaClient?
        get() = GCKCastContext.sharedInstance().sessionManager.currentCastSession?.remoteMediaClient

    private val remoteListener = object : NSObject(), GCKRemoteMediaClientListenerProtocol {
        override fun remoteMediaClient(
            client: GCKRemoteMediaClient,
            didUpdateMediaStatus: GCKMediaStatus?
        ) {
            val url = didUpdateMediaStatus?.mediaInformation?.contentURL?.absoluteString
            if (url != null) {
                val idx = items.indexOfFirst { it.remoteUrl == url }
                if (idx >= 0) remoteIndex = idx
            }
            onStateChanged?.invoke()
        }
    }

    private val sessionListener = object : NSObject(), GCKSessionManagerListenerProtocol {
        @ObjCSignatureOverride
        override fun sessionManager(sessionManager: GCKSessionManager, didStartSession: GCKSession) {
            AppLog.d(TAG, "Cast session started")
            beginCasting()
        }

        @ObjCSignatureOverride
        override fun sessionManager(sessionManager: GCKSessionManager, didResumeSession: GCKSession) {
            AppLog.d(TAG, "Cast session resumed")
            beginCasting()
        }

        override fun sessionManager(
            sessionManager: GCKSessionManager,
            didEndSession: GCKSession,
            withError: NSError?
        ) {
            AppLog.d(TAG, "Cast session ended")
            endCasting()
        }
    }

    private fun beginCasting() {
        if (items.isEmpty()) {
            casting = true
            onStateChanged?.invoke()
            return
        }
        val startWindow = local.currentWindowIndex
        val startPositionMs = local.currentPositionInWindowMs
        val wasPlaying = local.isPlaying
        local.pause()

        casting = true
        remoteIndex = startWindow
        remoteWasPlaying = wasPlaying

        loadRemoteQueue(startWindow, startPositionMs)
        remoteClient?.addListener(remoteListener)
        onStateChanged?.invoke()
    }

    private fun endCasting() {
        if (!casting) return
        val resumeWindow = remoteIndex
        val resumePositionMs = remotePositionMs()
        val wasPlaying = remoteIsPlaying()
        casting = false

        if (items.isNotEmpty()) {
            local.seekTo(resumeWindow, resumePositionMs)
            if (wasPlaying || remoteWasPlaying) local.play()
        }
        onStateChanged?.invoke()
    }

    private fun loadRemoteQueue(startWindow: Int, startPositionMs: Long) {
        val client = remoteClient ?: return
        val queueItems = items.map { it.toQueueItem() }
        val options = GCKMediaQueueLoadOptions().apply {
            startIndex = startWindow.toULong()
            playPosition = startPositionMs / 1000.0
        }
        client.queueLoadItems(queueItems, withOptions = options)
    }

    private fun PlayerItem.toQueueItem(): GCKMediaQueueItem {
        val meta = GCKMediaMetadata(metadataType = GCKMediaMetadataTypeMusicTrack)
        meta.setString(title, forKey = kGCKMetadataKeyTitle!!)
        meta.setString(artist, forKey = kGCKMetadataKeyArtist!!)
        meta.setString(albumTitle, forKey = kGCKMetadataKeyAlbumTitle!!)

        val info = GCKMediaInformationBuilder(contentURL = NSURL.URLWithString(remoteUrl)!!).apply {
            streamType = GCKMediaStreamTypeBuffered
            contentType = "audio/mpeg"
            metadata = meta
        }.build()

        return GCKMediaQueueItemBuilder().apply {
            mediaInformation = info
            autoplay = true
        }.build()
    }

    private fun remotePositionMs(): Long {
        val client = remoteClient ?: return 0L
        return (client.approximateStreamPosition() * 1000).toLong().coerceAtLeast(0)
    }

    private fun remoteIsPlaying(): Boolean {
        val state = remoteClient?.mediaStatus?.playerState
        return state == GCKMediaPlayerStatePlaying || state == GCKMediaPlayerStateBuffering
    }

    // ---- PlatformPlayer ----

    override fun connect() {
        local.connect()
        if (!listenersRegistered) {
            listenersRegistered = true
            try {
                GCKCastContext.sharedInstance().sessionManager.addListener(sessionListener)
                // A session may already be active (e.g. reconnect after relaunch).
                if (GCKCastContext.sharedInstance().sessionManager.currentCastSession != null) {
                    casting = true
                    remoteClient?.addListener(remoteListener)
                }
            } catch (e: Exception) {
                AppLog.w(TAG, "Cast listeners unavailable", e)
            }
        }
        local.setOnStateChanged { onStateChanged?.invoke() }
    }

    override fun disconnect() {
        local.disconnect()
    }

    override fun setItems(
        items: List<PlayerItem>,
        startWindow: Int,
        startPositionMs: Long,
        autoPlay: Boolean
    ) {
        this.items = items
        if (casting) {
            remoteIndex = startWindow
            remoteWasPlaying = autoPlay
            loadRemoteQueue(startWindow, startPositionMs)
            remoteClient?.addListener(remoteListener)
            // Keep the local player loaded (paused) so ending the session resumes seamlessly.
            local.setItems(items, startWindow, startPositionMs, autoPlay = false)
        } else {
            local.setItems(items, startWindow, startPositionMs, autoPlay)
        }
    }

    override fun addItems(items: List<PlayerItem>) {
        this.items = this.items + items
        local.addItems(items)
        // Receiver-side append is skipped: live-day appends mid-cast are re-synced on the
        // next setItems or session change. (Rare path; keeps the queue logic simple.)
    }

    override fun clearItemsAndStop() {
        if (casting) remoteClient?.stop()
        local.clearItemsAndStop()
        items = emptyList()
    }

    override fun play() {
        if (casting) remoteClient?.play() else local.play()
    }

    override fun pause() {
        if (casting) remoteClient?.pause() else local.pause()
    }

    override fun seekTo(windowIndex: Int, positionMs: Long) {
        if (casting) {
            if (windowIndex == remoteIndex) {
                remoteClient?.seekWithOptions(GCKMediaSeekOptions().apply {
                    interval = positionMs / 1000.0
                })
            } else {
                remoteIndex = windowIndex
                loadRemoteQueue(windowIndex, positionMs)
            }
        } else {
            local.seekTo(windowIndex, positionMs)
        }
    }

    override fun setSpeed(speed: Float) {
        local.setSpeed(speed)
        if (casting) remoteClient?.setPlaybackRate(speed)
    }

    override val itemCount: Int
        get() = items.size

    override val currentWindowIndex: Int
        get() = if (casting) remoteIndex else local.currentWindowIndex

    override val currentPositionInWindowMs: Long
        get() = if (casting) remotePositionMs() else local.currentPositionInWindowMs

    override val isPlaying: Boolean
        get() = if (casting) remoteIsPlaying() else local.isPlaying

    override val speed: Float
        get() = local.speed

    override val isReadyOrBuffering: Boolean
        get() = if (casting) remoteClient?.mediaStatus != null else local.isReadyOrBuffering

    override fun setOnStateChanged(callback: (() -> Unit)?) {
        onStateChanged = callback
        local.setOnStateChanged(callback?.let { { it() } })
    }
}
