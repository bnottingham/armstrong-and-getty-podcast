package com.nomnomsom.armstrongandgetty.media

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null

    companion object {
        const val ACTION_SEEK_BACK_30 = "com.nomnomsom.armstrongandgetty.SEEK_BACK_30"
        const val ACTION_SEEK_FORWARD_30 = "com.nomnomsom.armstrongandgetty.SEEK_FORWARD_30"

        private val SEEK_BACK_COMMAND = SessionCommand(ACTION_SEEK_BACK_30, Bundle.EMPTY)
        private val SEEK_FORWARD_COMMAND = SessionCommand(ACTION_SEEK_FORWARD_30, Bundle.EMPTY)
    }

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

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent ?: Intent(),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .setCallback(MediaSessionCallback())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        exoPlayer = null
        super.onDestroy()
    }

    private inner class MediaSessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SEEK_BACK_COMMAND)
                .add(SEEK_FORWARD_COMMAND)
                .build()

            // Define the notification button layout:
            // [Seek Back 30s] [Play/Pause] [Seek Forward 30s]
            val seekBackButton = CommandButton.Builder(CommandButton.ICON_SKIP_BACK_30)
                .setDisplayName("Back 30s")
                .setSessionCommand(SEEK_BACK_COMMAND)
                .build()

            val seekForwardButton = CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_30)
                .setDisplayName("Forward 30s")
                .setSessionCommand(SEEK_FORWARD_COMMAND)
                .build()

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setCustomLayout(ImmutableList.of(seekBackButton, seekForwardButton))
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                ACTION_SEEK_BACK_30 -> {
                    session.player.seekTo(
                        (session.player.currentPosition - 30_000).coerceAtLeast(0)
                    )
                }
                ACTION_SEEK_FORWARD_30 -> {
                    session.player.seekTo(
                        (session.player.currentPosition + 30_000)
                            .coerceAtMost(session.player.duration)
                    )
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }
}