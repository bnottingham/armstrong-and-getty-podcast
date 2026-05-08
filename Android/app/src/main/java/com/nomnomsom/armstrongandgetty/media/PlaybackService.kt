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
import com.nomnomsom.armstrongandgetty.R
import com.nomnomsom.armstrongandgetty.data.local.PodcastDayDao
import com.nomnomsom.armstrongandgetty.media.PlaybackController.Companion.EXTRA_REMOTE_URL
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@OptIn(UnstableApi::class)
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

        // CastContext fails on devices without Google Play Services — fall back to ExoPlayer only.
        try {
            val castContext = CastContext.getSharedInstance(this)
            castPlayer = CastPlayer(castContext)
        } catch (_: Exception) {
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

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build()
        )

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

        // CastPlayer can't stream file:// URIs, so swap in the original remote URL
        // we stashed in the MediaItem's extras when handing off to CastPlayer.
        val mediaItems = mutableListOf<MediaItem>()
        for (i in 0 until oldPlayer.mediaItemCount) {
            val oldItem = oldPlayer.getMediaItemAt(i)
            val uri = oldItem.localConfiguration?.uri
            val finalUri = if (uri?.scheme == "file") {
                oldItem.mediaMetadata.extras?.getString(EXTRA_REMOTE_URL)?.let { Uri.parse(it) } ?: uri
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
        // Keep alive during active playback or an active Cast session; otherwise stop.
        val player = mediaSession?.player
        if (player == null || (!player.playWhenReady && player !is CastPlayer)) {
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
            // Samsung's media notification places custom buttons around play/pause in rotation:
            // index 0 → nearest-left, index 1 → nearest-right, index 2 → far-left, index 3 → far-right.
            // This ordering lands as [Back10, Back30, play, Fwd30, Fwd10] on-device.
            val customLayout = ImmutableList.of(
                CommandButton.Builder()
                    .setDisplayName("Backward 30s")
                    .setSessionCommand(SessionCommand("BACKWARD_30", Bundle.EMPTY))
                    .setIconResId(R.drawable.ic_replay_30)
                    .build(),
                CommandButton.Builder()
                    .setDisplayName("Forward 30s")
                    .setSessionCommand(SessionCommand("FORWARD_30", Bundle.EMPTY))
                    .setIconResId(R.drawable.ic_forward_30)
                    .build(),
                CommandButton.Builder()
                    .setDisplayName("Backward 10s")
                    .setSessionCommand(SessionCommand("BACKWARD_10", Bundle.EMPTY))
                    .setIconResId(R.drawable.ic_replay_10)
                    .build(),
                CommandButton.Builder()
                    .setDisplayName("Forward 10s")
                    .setSessionCommand(SessionCommand("FORWARD_10", Bundle.EMPTY))
                    .setIconResId(R.drawable.ic_forward_10)
                    .build()
            )
            // Disable the "skip to next/previous media item" player commands so external controllers
            // (Bluetooth car decks, Android Auto, the system media notification) fall back to
            // SEEK_FORWARD / SEEK_BACK, which honour ExoPlayer's 30s increment. In-segment skipping
            // stays available inside the app via the details screen (it uses seekTo directly).
            val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                .remove(Player.COMMAND_SEEK_TO_NEXT)
                .remove(Player.COMMAND_SEEK_TO_PREVIOUS)
                .remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .build()

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(SessionCommand("BACKWARD_30", Bundle.EMPTY))
                        .add(SessionCommand("BACKWARD_10", Bundle.EMPTY))
                        .add(SessionCommand("FORWARD_10", Bundle.EMPTY))
                        .add(SessionCommand("FORWARD_30", Bundle.EMPTY))
                        .build()
                )
                .setAvailablePlayerCommands(playerCommands)
                .setCustomLayout(customLayout)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            val deltaMs = when (customCommand.customAction) {
                "BACKWARD_30" -> -30_000L
                "BACKWARD_10" -> -10_000L
                "FORWARD_10" -> 10_000L
                "FORWARD_30" -> 30_000L
                else -> null
            }
            if (deltaMs != null) {
                val newPos = (session.player.currentPosition + deltaMs).coerceAtLeast(0)
                session.player.seekTo(newPos)
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
