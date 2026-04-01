package com.nomnomsom.armstrongandgetty.media

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nomnomsom.armstrongandgetty.data.local.PodcastDayDao
import com.nomnomsom.armstrongandgetty.data.model.DownloadState
import com.nomnomsom.armstrongandgetty.data.model.Segment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {

    @Inject lateinit var dao: PodcastDayDao
    @Inject lateinit var gson: Gson

    private var mediaSession: MediaLibrarySession? = null
    private var exoPlayer: ExoPlayer? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        const val ACTION_SEEK_BACK_30 = "com.nomnomsom.armstrongandgetty.SEEK_BACK_30"
        const val ACTION_SEEK_FORWARD_30 = "com.nomnomsom.armstrongandgetty.SEEK_FORWARD_30"

        private val SEEK_BACK_COMMAND = SessionCommand(ACTION_SEEK_BACK_30, Bundle.EMPTY)
        private val SEEK_FORWARD_COMMAND = SessionCommand(ACTION_SEEK_FORWARD_30, Bundle.EMPTY)

        // Browse tree node IDs
        const val ROOT_ID = "root"
        const val RECENT_EPISODES_ID = "recent_episodes"
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

        mediaSession = MediaLibrarySession.Builder(this, player, LibrarySessionCallback())
            .setSessionActivity(pendingIntent)
            .build()

        // Periodically save progress locally (covers Android Auto playback without in-app UI)
        serviceScope.launch {
            while (true) {
                delay(10_000) // Every 10 seconds
                val p = exoPlayer ?: continue
                if (!p.isPlaying || p.mediaItemCount == 0) continue

                val currentItem = p.currentMediaItem ?: continue
                val mediaId = currentItem.mediaId

                // Extract date from mediaId format "seg:2026-03-05:0"
                if (mediaId.startsWith("seg:")) {
                    val parts = mediaId.removePrefix("seg:").split(":")
                    if (parts.size == 2) {
                        val date = parts[0]
                        val posMs = p.currentPosition
                        val duration = p.duration
                        val isListened = duration > 0 && posMs >= duration - 5000

                        dao.updateListenProgress(date, posMs, isListened)
                    }
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
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

    // ── Helper: parse segments JSON ──

    private fun parseSegments(json: String): List<Segment> {
        return try {
            val type = object : TypeToken<List<Segment>>() {}.type
            gson.fromJson<List<Segment>>(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun getSegmentFilePath(date: String, segmentIndex: Int): String {
        val podcastDir = File(filesDir, "podcasts")
        return File(podcastDir, "ag_${date}_seg${segmentIndex}.mp3").absolutePath
    }

    // ── Library + Session callback ──

    private inner class LibrarySessionCallback : MediaLibrarySession.Callback {

        // ── Connection: grant session commands + custom layout ──

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                .buildUpon()
                .add(SEEK_BACK_COMMAND)
                .add(SEEK_FORWARD_COMMAND)
                .build()

            // Remove skip-next/skip-previous so notification & Android Auto only show:
            // [⏪ 30s]  [⏯ Play/Pause]  [⏩ 30s]
            val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                .remove(Player.COMMAND_SEEK_TO_NEXT)
                .remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .remove(Player.COMMAND_SEEK_TO_PREVIOUS)
                .remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .build()

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
                .setAvailablePlayerCommands(playerCommands)
                .setCustomLayout(ImmutableList.of(seekBackButton, seekForwardButton))
                .build()
        }

        // ── Custom commands: seek ±30s ──

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

        // ── Browse tree: root ──

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val root = MediaItem.Builder()
                .setMediaId(ROOT_ID)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("Armstrong & Getty")
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS)
                        .build()
                )
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(root, params))
        }

        // ── Browse tree: children ──

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return serviceScope.future(Dispatchers.IO) {
                when (parentId) {
                    ROOT_ID -> {
                        // Single folder: "Recent Episodes"
                        val folder = MediaItem.Builder()
                            .setMediaId(RECENT_EPISODES_ID)
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle("Recent Episodes")
                                    .setIsBrowsable(true)
                                    .setIsPlayable(false)
                                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS)
                                    .build()
                            )
                            .build()
                        LibraryResult.ofItemList(ImmutableList.of(folder), params)
                    }

                    RECENT_EPISODES_ID -> {
                        // Flat list of downloaded days
                        val days = dao.getAllDownloadedDays()
                        val items = days.map { day ->
                            val segCount = day.segmentCount
                            val durationMin = (day.totalDurationMs / 60_000).toInt()
                            MediaItem.Builder()
                                .setMediaId("day:${day.date}")
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setTitle(day.title)
                                        .setSubtitle("$segCount segments · ${durationMin}min")
                                        .setArtist("Armstrong & Getty")
                                        .setIsBrowsable(false)
                                        .setIsPlayable(true)
                                        .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
                                        .build()
                                )
                                .build()
                        }
                        LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
                    }

                    else -> {
                        LibraryResult.ofItemList(ImmutableList.of(), params)
                    }
                }
            }
        }

        // ── Resolve a media item for playback ──

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            return serviceScope.future(Dispatchers.IO) {
                if (mediaId.startsWith("day:")) {
                    val date = mediaId.removePrefix("day:")
                    val day = dao.getDayByDate(date)
                    if (day != null) {
                        val item = MediaItem.Builder()
                            .setMediaId(mediaId)
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(day.title)
                                    .setArtist("Armstrong & Getty")
                                    .setIsPlayable(true)
                                    .setIsBrowsable(false)
                                    .build()
                            )
                            .build()
                        LibraryResult.ofItem(item, null)
                    } else {
                        LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
                    }
                } else {
                    LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
                }
            }
        }

        // ── When Android Auto selects an item to play ──

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>
        ): ListenableFuture<List<MediaItem>> {
            return serviceScope.future(Dispatchers.IO) {
                val resolved = mutableListOf<MediaItem>()

                for (item in mediaItems) {
                    val mediaId = item.mediaId
                    if (mediaId.startsWith("day:")) {
                        val date = mediaId.removePrefix("day:")
                        val day = dao.getDayByDate(date)
                        if (day != null && day.downloadState == DownloadState.DOWNLOADED.value) {
                            val segments = parseSegments(day.segmentsJson)
                            // Build playable MediaItems from segment files on disk
                            segments.forEachIndexed { index, seg ->
                                val filePath = getSegmentFilePath(date, index)
                                val segItem = MediaItem.Builder()
                                    .setMediaId("seg:${date}:$index")
                                    .setUri(Uri.parse("file://$filePath"))
                                    .setMediaMetadata(
                                        MediaMetadata.Builder()
                                            .setTitle("${day.title} — Hr ${seg.hour}")
                                            .setArtist("Armstrong & Getty")
                                            .setAlbumTitle("Armstrong & Getty On Demand")
                                            .setTrackNumber(index + 1)
                                            .build()
                                    )
                                    .build()
                                resolved.add(segItem)
                            }
                        }
                    } else {
                        // Pass through other items (e.g. already-resolved URIs)
                        resolved.add(item)
                    }
                }

                resolved
            }
        }
    }
}
