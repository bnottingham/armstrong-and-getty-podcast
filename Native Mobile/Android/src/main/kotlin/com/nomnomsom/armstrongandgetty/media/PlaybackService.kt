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
import androidx.media3.common.ForwardingPlayer
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
import com.nomnomsom.armstrongandgetty.data.model.displayLabel
import com.nomnomsom.armstrongandgetty.data.repository.PodcastRepository
import com.nomnomsom.armstrongandgetty.media.PlaybackController.Companion.EXTRA_LOCAL_PATH
import com.nomnomsom.armstrongandgetty.media.PlaybackController.Companion.EXTRA_REMOTE_URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService(), KoinComponent {

    private val repository: PodcastRepository by inject()

    /** Backs Android Auto's browse tree and the mediaId-only items it hands back when tapped. */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var exoPlayer: ExoPlayer? = null
    private var castPlayer: CastPlayer? = null

    /** The Cast player as handed to the session — wrapped with the seek bounds guard. */
    private var castSessionPlayer: Player? = null
    private var mediaSession: MediaLibrarySession? = null

    /**
     * CastPlayer's timeline stays empty until the Cast receiver reports its queue back, and
     * RemoteCastPlayer.seekTo indexes into that timeline without a bounds check — an
     * index-based seek flushed from a controller's command queue during that window crashes
     * with ArrayIndexOutOfBoundsException. Drop out-of-range index seeks instead.
     */
    private class BoundsCheckedPlayer(player: Player) : ForwardingPlayer(player) {
        override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
            if (mediaItemIndex >= currentTimeline.windowCount) return
            super.seekTo(mediaItemIndex, positionMs)
        }

        override fun seekToDefaultPosition(mediaItemIndex: Int) {
            if (mediaItemIndex >= currentTimeline.windowCount) return
            super.seekToDefaultPosition(mediaItemIndex)
        }
    }

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
            val cast = CastPlayer(castContext)
            castPlayer = cast
            castSessionPlayer = BoundsCheckedPlayer(cast)
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
                castSessionPlayer?.let { switchToPlayer(it) }
            }

            override fun onCastSessionUnavailable() {
                switchToPlayer(exoPlayer!!)
            }
        })
    }

    /**
     * Rewrite an item's URI for the player that will actually play it: the Cast receiver
     * can't reach file:// paths on the phone, and local playback should prefer the
     * downloaded file over streaming. Both URLs ride in the item's metadata extras.
     */
    private fun resolveUriFor(item: MediaItem, forCast: Boolean): MediaItem {
        val currentUri = item.localConfiguration?.uri ?: return item
        val extras = item.mediaMetadata.extras
        val targetUri = if (forCast) {
            if (currentUri.scheme == "file") {
                extras?.getString(EXTRA_REMOTE_URL)
                    ?.takeIf { it.isNotEmpty() }
                    ?.let(Uri::parse) ?: currentUri
            } else {
                currentUri
            }
        } else {
            extras?.getString(EXTRA_LOCAL_PATH)
                ?.takeIf { File(it).length() > 0 }
                ?.let { Uri.parse("file://$it") }
                ?: currentUri
        }
        return if (targetUri == currentUri) {
            item
        } else {
            item.buildUpon().setUri(targetUri).setMimeType(MimeTypes.AUDIO_MPEG).build()
        }
    }

    private fun resolveUrisForActivePlayer(items: List<MediaItem>): List<MediaItem> {
        val casting = mediaSession?.player === castSessionPlayer
        return items.map { resolveUriFor(it, forCast = casting) }
    }

    @OptIn(UnstableApi::class)
    private fun switchToPlayer(newPlayer: Player) {
        val session = mediaSession ?: return
        val oldPlayer = session.player
        if (oldPlayer === newPlayer) return

        val toCast = newPlayer === castSessionPlayer
        val mediaItems = mutableListOf<MediaItem>()
        for (i in 0 until oldPlayer.mediaItemCount) {
            mediaItems.add(resolveUriFor(oldPlayer.getMediaItemAt(i), forCast = toCast))
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
        val isCasting = player != null && player === castSessionPlayer
        if (player == null || (!player.playWhenReady && !isCasting)) {
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
        serviceScope.cancel()
        super.onDestroy()
    }

    private inner class MediaLibraryCallback : MediaLibrarySession.Callback {
        @OptIn(UnstableApi::class)
        override fun onConnect(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            // Media3's built-in skip icons (ICON_SKIP_*) render crisply in every
            // notification shell — hand-vendored drawables with theme-attr tints don't.
            // Slot hints place ±30 beside play/pause and ±10 outside them; the system
            // resolves layout per surface (phone notification, Wear, Auto).
            val mediaButtonPreferences = ImmutableList.of(
                CommandButton.Builder(CommandButton.ICON_SKIP_BACK_30)
                    .setDisplayName("Back 30 seconds")
                    .setSessionCommand(SessionCommand("BACKWARD_30", Bundle.EMPTY))
                    .setSlots(CommandButton.SLOT_BACK)
                    .build(),
                CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_30)
                    .setDisplayName("Forward 30 seconds")
                    .setSessionCommand(SessionCommand("FORWARD_30", Bundle.EMPTY))
                    .setSlots(CommandButton.SLOT_FORWARD)
                    .build(),
                CommandButton.Builder(CommandButton.ICON_SKIP_BACK_10)
                    .setDisplayName("Back 10 seconds")
                    .setSessionCommand(SessionCommand("BACKWARD_10", Bundle.EMPTY))
                    .setSlots(CommandButton.SLOT_BACK_SECONDARY)
                    .build(),
                CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_10)
                    .setDisplayName("Forward 10 seconds")
                    .setSessionCommand(SessionCommand("FORWARD_10", Bundle.EMPTY))
                    .setSlots(CommandButton.SLOT_FORWARD_SECONDARY)
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

            // Auto's browse tree calls (onGetLibraryRoot/onGetChildren) are gated behind the
            // library commands, not just the session commands — granting only
            // DEFAULT_SESSION_COMMANDS makes MediaSessionStub reject every browse request with
            // RESULT_ERROR_NOT_SUPPORTED before it ever reaches this callback, which Android
            // Auto surfaces as "doesn't seem to be working right now".
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                        .add(SessionCommand("BACKWARD_30", Bundle.EMPTY))
                        .add(SessionCommand("BACKWARD_10", Bundle.EMPTY))
                        .add(SessionCommand("FORWARD_10", Bundle.EMPTY))
                        .add(SessionCommand("FORWARD_30", Bundle.EMPTY))
                        .build()
                )
                .setAvailablePlayerCommands(playerCommands)
                .setMediaButtonPreferences(mediaButtonPreferences)
                .build()
        }

        /**
         * Controllers always send items with file:// URIs (remote URL in extras). Resolve
         * them here — the documented hook for rewriting playable URIs — so playlists set
         * or appended WHILE a Cast session is active reach the receiver as streamable
         * https URLs. Without this, the receiver's queue load fails silently and
         * play() is a no-op.
         *
         * External controllers (Android Auto tapping a browsed episode) instead send a
         * single mediaId-only item with no URI at all — those are expanded into the day's
         * real segment playlist below, rather than handed to ExoPlayer with nothing to play.
         */
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val episodeRequest = mediaItems.singleOrNull()?.takeIf {
                it.localConfiguration == null && it.mediaId.startsWith(EPISODE_MEDIA_ID_PREFIX)
            }
            if (episodeRequest != null) {
                return serviceScope.future {
                    val date = episodeRequest.mediaId.removePrefix(EPISODE_MEDIA_ID_PREFIX)
                    val expanded = buildPlaylistForDay(date)
                    MediaSession.MediaItemsWithStartPosition(
                        expanded.ifEmpty { resolveUrisForActivePlayer(mediaItems) },
                        0,
                        0L
                    )
                }
            }
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(
                    resolveUrisForActivePlayer(mediaItems),
                    startIndex,
                    startPositionMs
                )
            )
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val episodeRequest = mediaItems.singleOrNull()?.takeIf {
                it.localConfiguration == null && it.mediaId.startsWith(EPISODE_MEDIA_ID_PREFIX)
            }
            if (episodeRequest != null) {
                return serviceScope.future {
                    val date = episodeRequest.mediaId.removePrefix(EPISODE_MEDIA_ID_PREFIX)
                    buildPlaylistForDay(date).ifEmpty { resolveUrisForActivePlayer(mediaItems) }
                        .toMutableList()
                }
            }
            return Futures.immediateFuture(
                resolveUrisForActivePlayer(mediaItems).toMutableList()
            )
        }

        /** Expands a browsed day into its real, playable segment MediaItems. */
        private suspend fun buildPlaylistForDay(date: String): List<MediaItem> {
            val day = repository.getDayByDate(date) ?: return emptyList()
            val segments = repository.parseSegments(day.segmentsJson)
            if (segments.isEmpty()) return emptyList()
            val filePaths = repository.getSegmentFilePaths(date, segments.size)

            return segments.mapIndexedNotNull { index, segment ->
                val localPath = filePaths.getOrNull(index)?.takeIf { File(it).length() > 0 }
                val uri = when {
                    localPath != null -> Uri.parse("file://$localPath")
                    segment.audioUrl.isNotBlank() -> Uri.parse(segment.audioUrl)
                    else -> return@mapIndexedNotNull null
                }
                val extras = Bundle().apply {
                    putString(EXTRA_REMOTE_URL, segment.audioUrl)
                    localPath?.let { putString(EXTRA_LOCAL_PATH, it) }
                }
                MediaItem.Builder()
                    .setMediaId("$EPISODE_MEDIA_ID_PREFIX$date#$index")
                    .setUri(uri)
                    .setMimeType(MimeTypes.AUDIO_MPEG)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle("${day.title} — ${segment.displayLabel}")
                            .setArtist("Armstrong & Getty")
                            .setAlbumTitle("Armstrong & Getty On Demand")
                            .setTrackNumber(index + 1)
                            .setIsBrowsable(false)
                            .setIsPlayable(true)
                            .setExtras(extras)
                            .build()
                    )
                    .build()
            }
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
                .setMediaId(ROOT_ID)
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
            if (parentId != ROOT_ID) {
                return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of(), params))
            }
            return serviceScope.future {
                LibraryResult.ofItemList(ImmutableList.copyOf(loadBrowsableDays()), params)
            }
        }

        /**
         * Android Auto can bind straight to this service without the app UI ever having run,
         * so the RSS feed that normally gets primed by [EpisodeListViewModel]'s init or the
         * periodic worker may never have fired. Refresh once if the local catalog is empty
         * so a fresh install still has something to browse.
         */
        private suspend fun loadBrowsableDays(): List<MediaItem> {
            var days = repository.getAllDaysSnapshot()
            if (days.isEmpty()) {
                repository.refreshFeed()
                days = repository.getAllDaysSnapshot()
            }
            return days
                .filter { it.segmentCount > 0 }
                .sortedByDescending { it.date }
                .take(30)
                .map { day ->
                    MediaItem.Builder()
                        .setMediaId("$EPISODE_MEDIA_ID_PREFIX${day.date}")
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(day.title)
                                .setArtist("Armstrong & Getty")
                                .setAlbumTitle("Armstrong & Getty On Demand")
                                .setIsBrowsable(false)
                                .setIsPlayable(true)
                                .build()
                        )
                        .build()
                }
        }
    }

    private companion object {
        private const val ROOT_ID = "ROOT"
        private const val EPISODE_MEDIA_ID_PREFIX = "episode:"
    }
}
