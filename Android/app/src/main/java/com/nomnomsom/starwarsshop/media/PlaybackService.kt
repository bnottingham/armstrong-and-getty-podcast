package com.nomnomsom.starwarsshop.media

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null

    companion object {
        const val ACTION_SEEK_BACK_15 = "com.nomnomsom.starwarsshop.SEEK_BACK_15"
        const val ACTION_SEEK_FORWARD_15 = "com.nomnomsom.starwarsshop.SEEK_FORWARD_15"

        private val SEEK_BACK_COMMAND = SessionCommand(ACTION_SEEK_BACK_15, Bundle.EMPTY)
        private val SEEK_FORWARD_COMMAND = SessionCommand(ACTION_SEEK_FORWARD_15, Bundle.EMPTY)
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
            .setSeekBackIncrementMs(15_000)
            .setSeekForwardIncrementMs(15_000)
            .build()

        exoPlayer = player

        // Create the activity intent for when user taps notification
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

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                ACTION_SEEK_BACK_15 -> {
                    session.player.seekTo(
                        (session.player.currentPosition - 15_000).coerceAtLeast(0)
                    )
                }
                ACTION_SEEK_FORWARD_15 -> {
                    session.player.seekTo(
                        (session.player.currentPosition + 15_000)
                            .coerceAtMost(session.player.duration)
                    )
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }
}
