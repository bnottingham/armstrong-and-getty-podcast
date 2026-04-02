package com.nomnomsom.armstrongandgetty.media

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.android.gms.cast.framework.CastContext
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.nomnomsom.armstrongandgetty.data.local.PodcastDayDao
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {

    @Inject
    lateinit var podcastDayDao: PodcastDayDao

    private var exoPlayer: ExoPlayer? = null
    private var castPlayer: CastPlayer? = null
    private var mediaSession: MediaLibrarySession? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(30_000)
            .setSeekForwardIncrementMs(30_000)
            .build()

        exoPlayer = player

        // Initialize CastPlayer
        try {
            val castContext = CastContext.getSharedInstance(this)
            castPlayer = CastPlayer(castContext)
        } catch (e: Exception) {
            // Cast context might fail if Play Services are missing or not initialized
        }

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent ?: Intent(),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaLibrarySession.Builder(
            this,
            player,
            MediaLibraryCallback()
        )
            .setSessionActivity(pendingIntent)
            .build()

        // Set up notification so the service stays alive when backgrounded
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build()
        )

        // Automatically switch between ExoPlayer and CastPlayer
        castPlayer?.setSessionAvailabilityListener(object : SessionAvailabilityListener {
            override fun onCastSessionAvailable() {
                switchToPlayer(castPlayer!!)
            }

            override fun onCastSessionUnavailable() {
                switchToPlayer(exoPlayer!!)
            }
        })
    }

    @OptIn(UnstableApi::class)
    private fun switchToPlayer(newPlayer: Player) {
        val session = mediaSession ?: return
        val oldPlayer = session.player
        if (oldPlayer === newPlayer) return

        // Transfer state to new player
        val mediaItems = mutableListOf<MediaItem>()
        for (i in 0 until oldPlayer.mediaItemCount) {
            val oldItem = oldPlayer.getMediaItemAt(i)
            // Ensure URI is usable by Cast (no file:// URIs)
            val uri = oldItem.localConfiguration?.uri
            val finalUri = if (uri?.scheme == "file") {
                // If it's a local file, we need the original remote URL for casting
                // We'll rely on the mediaId or metadata if we stored it there
                oldItem.mediaMetadata.extras?.getString("remote_url")?.let { Uri.parse(it) } ?: uri
            } else {
                uri
            }

            mediaItems.add(
                oldItem.buildUpon()
                    .setUri(finalUri)
                    .setMimeType(MimeTypes.AUDIO_MPEG)
                    .build()
            )
        }

        val currentWindowIndex = oldPlayer.currentMediaItemIndex
        val currentPositionMs = oldPlayer.currentPosition
        val playWhenReady = oldPlayer.playWhenReady

        // Stop and clear old player AFTER grabbing items
        oldPlayer.stop()
        oldPlayer.clearMediaItems()

        newPlayer.setMediaItems(mediaItems, currentWindowIndex, currentPositionMs)
        newPlayer.prepare()
        newPlayer.playWhenReady = playWhenReady

        session.player = newPlayer
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player != null && (player.playWhenReady || player is CastPlayer)) {
            // Keep the service alive for active playback or active Cast session
        } else {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        exoPlayer?.release()
        exoPlayer = null
        castPlayer?.release()
        castPlayer = null
        super.onDestroy()
    }

    private inner class MediaLibraryCallback : MediaLibrarySession.Callback {
        @OptIn(UnstableApi::class)
        override fun onConnect(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val customLayout = ImmutableList.of(
                CommandButton.Builder()
                    .setDisplayName("Forward 30s")
                    .setSessionCommand(SessionCommand("FORWARD_30", Bundle.EMPTY))
                    .setIconResId(android.R.drawable.ic_media_ff)
                    .build(),
                CommandButton.Builder()
                    .setDisplayName("Backward 30s")
                    .setSessionCommand(SessionCommand("BACKWARD_30", Bundle.EMPTY))
                    .setIconResId(android.R.drawable.ic_media_rew)
                    .build()
            )
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(SessionCommand("FORWARD_30", Bundle.EMPTY))
                        .add(SessionCommand("BACKWARD_30", Bundle.EMPTY))
                        .build()
                )
                .setCustomLayout(customLayout)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                "FORWARD_30" -> {
                    val currentPos = session.player.currentPosition
                    session.player.seekTo(currentPos + 30_000)
                }
                "BACKWARD_30" -> {
                    val currentPos = session.player.currentPosition
                    session.player.seekTo((currentPos - 30_000).coerceAtLeast(0))
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val rootItem = MediaItem.Builder()
                .setMediaId("ROOT")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setTitle("Armstrong & Getty")
                        .build()
                )
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of(), params))
        }
    }
}