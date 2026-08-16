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
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.android.gms.cast.framework.CastContext
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.nomnomsom.armstrongandgetty.media.PlaybackController.Companion.EXTRA_LOCAL_PATH
import com.nomnomsom.armstrongandgetty.media.PlaybackController.Companion.EXTRA_REMOTE_URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.io.File

@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {

    /** Read-only view of the episode library, shared with the app UI (Koin singleton). */
    private val mediaCatalog: MediaCatalog by inject()

    /** Background work for the async library callbacks (browse, search, play-request expansion). */
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

    private fun isCasting(): Boolean = mediaSession?.player === castSessionPlayer

    /**
     * Build a fully-playable queue item for one catalog segment. Carries BOTH URLs in
     * extras (so [switchToPlayer] can re-resolve on Cast handoff) and sets the initial URI
     * for whichever player is active: Cast → remote https; local → the downloaded file when
     * present, otherwise the remote stream (an episode the user never downloaded still plays
     * in the car).
     */
    private fun queueItem(seg: CatalogSegment, forCast: Boolean): MediaItem {
        val localFileUri = seg.localPath?.let { "file://$it" }
        val initialUri = if (forCast) {
            seg.remoteUrl.ifBlank { localFileUri.orEmpty() }
        } else {
            localFileUri ?: seg.remoteUrl
        }
        val extras = Bundle().apply {
            putString(EXTRA_REMOTE_URL, seg.remoteUrl)
            putString(EXTRA_LOCAL_PATH, seg.localPath.orEmpty())
        }
        return MediaItem.Builder()
            .setMediaId(seg.mediaId)
            .setUri(initialUri)
            .setMimeType(MimeTypes.AUDIO_MPEG)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(seg.title)
                    .setArtist("Armstrong & Getty")
                    .setAlbumTitle("Armstrong & Getty On Demand")
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .setExtras(extras)
                    .build()
            )
            .build()
    }

    private fun queueItemsFor(playback: CatalogPlayback, forCast: Boolean): List<MediaItem> =
        playback.segments.map { queueItem(it, forCast) }

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
        serviceScope.cancel()
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

    /** Bridge a suspend catalog call to a ListenableFuture the media library callbacks expect. */
    private fun <T> future(block: suspend () -> T): ListenableFuture<T> {
        val settable = SettableFuture.create<T>()
        serviceScope.launch {
            try {
                settable.set(block())
            } catch (t: Throwable) {
                settable.setException(t)
            }
        }
        return settable
    }

    // ---- browse-tree item builders --------------------------------------------------

    private fun rootItem(): MediaItem = MediaItem.Builder()
        .setMediaId(MEDIA_ID_ROOT)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .setTitle("Armstrong & Getty")
                .build()
        )
        .build()

    /** A browsable-tree leaf for an episode: playable, no URI (resolved at play time by id). */
    private fun episodeBrowseItem(ep: CatalogEpisode): MediaItem {
        val extras = Bundle().apply {
            putInt(
                MediaConstants.EXTRAS_KEY_COMPLETION_STATUS,
                when {
                    ep.isListened -> MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_FULLY_PLAYED
                    ep.listenedPositionMs > 0 -> MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED
                    else -> MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_NOT_PLAYED
                }
            )
            if (!ep.isListened && ep.listenedPositionMs > 0) {
                putDouble(MediaConstants.EXTRAS_KEY_COMPLETION_PERCENTAGE, ep.completionPercent / 100.0)
            }
        }
        return MediaItem.Builder()
            .setMediaId(ep.mediaId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(ep.title)
                    .setSubtitle(ep.subtitle)
                    .setArtist("Armstrong & Getty")
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
                    .setExtras(extras)
                    .build()
            )
            .build()
    }

    /** Browse-tree leaf for a single segment (used by onGetItem for deep links). */
    private fun segmentBrowseItem(seg: CatalogSegment): MediaItem = MediaItem.Builder()
        .setMediaId(seg.mediaId)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(seg.title)
                .setArtist("Armstrong & Getty")
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
                .build()
        )
        .build()

    /** Library params that ask Auto to render children as a list rather than a grid. */
    private fun listStyleParams(base: LibraryParams?): LibraryParams {
        val extras = Bundle(base?.extras ?: Bundle.EMPTY).apply {
            putInt(
                MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
            )
            putInt(
                MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
                MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
            )
        }
        return LibraryParams.Builder().setExtras(extras).build()
    }

    private fun <T> page(items: List<T>, page: Int, pageSize: Int): List<T> {
        if (pageSize <= 0) return items
        val from = page * pageSize
        if (from >= items.size) return emptyList()
        return items.subList(from, minOf(from + pageSize, items.size))
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

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    // Library commands (GET_LIBRARY_ROOT / GET_CHILDREN / GET_ITEM / SEARCH) must be
                    // granted or the legacy MediaBrowserService returns a null root and Android Auto's
                    // MediaBrowserCompat.connect() fails outright.
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
         * Every play request funnels through here — the app's own controller (items already
         * carry file:// URIs) AND Android Auto / Google Assistant / Bluetooth decks, whose
         * items carry only a mediaId or a search query and NO URI. Passing a URI-less item to
         * ExoPlayer throws NPE in DefaultMediaSourceFactory and takes down the whole process,
         * so the external requests are expanded into a real segment playlist here.
         */
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            // The app's own controller already sends fully-formed items (each has a URI).
            if (mediaItems.isNotEmpty() && mediaItems.all { it.localConfiguration != null }) {
                return Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(
                        resolveUrisForActivePlayer(mediaItems),
                        startIndex,
                        startPositionMs
                    )
                )
            }
            return expandExternalPlayRequest(mediaItems)
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            // Only the app appends items (live-segment growth); those already carry URIs. Anything
            // without a URI can't be turned into a source here (no start position context), so drop
            // it rather than hand ExoPlayer a null-URI item.
            val resolvable = mediaItems.filter { it.localConfiguration != null }
            return Futures.immediateFuture(
                resolveUrisForActivePlayer(resolvable).toMutableList()
            )
        }

        /**
         * Turn a single mediaId / searchQuery / mediaUri request from an external controller
         * into a concrete playlist with a start position. Never returns a URI-less item; if the
         * request resolves to nothing, returns an empty playlist (a safe no-op) rather than crash.
         */
        private fun expandExternalPlayRequest(
            mediaItems: List<MediaItem>
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = future {
            val request = mediaItems.firstOrNull()
            val query = request?.requestMetadata?.searchQuery
            val requestedId = request?.mediaId?.takeIf { it.isNotBlank() && it != MediaItem.DEFAULT_MEDIA_ID }

            val playback: CatalogPlayback? = when {
                !query.isNullOrBlank() ->
                    mediaCatalog.search(query)?.let { mediaCatalog.playbackForEpisode(it) }
                requestedId != null -> mediaCatalog.playbackFor(requestedId)
                // No id and no query (e.g. a bare "play") → resume the newest in-progress show.
                else -> mediaCatalog.resumeCandidate()?.let { mediaCatalog.playbackForEpisode(it) }
            }

            if (playback == null) {
                MediaSession.MediaItemsWithStartPosition(emptyList(), C.INDEX_UNSET, C.TIME_UNSET)
            } else {
                MediaSession.MediaItemsWithStartPosition(
                    queueItemsFor(playback, forCast = isCasting()),
                    playback.startSegmentIndex,
                    playback.startPositionInSegmentMs
                )
            }
        }

        /**
         * Media-button / "just play with nothing queued" resumption (System UI, Bluetooth,
         * Android Auto's play affordance). Resume the newest in-progress episode.
         */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = future {
            val playback = mediaCatalog.resumeCandidate()?.let { mediaCatalog.playbackForEpisode(it) }
                ?: return@future MediaSession.MediaItemsWithStartPosition(
                    emptyList(), C.INDEX_UNSET, C.TIME_UNSET
                )
            MediaSession.MediaItemsWithStartPosition(
                queueItemsFor(playback, forCast = isCasting()),
                playback.startSegmentIndex,
                playback.startPositionInSegmentMs
            )
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
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem(), listStyleParams(params)))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = future {
            if (parentId != MEDIA_ID_ROOT) {
                return@future LibraryResult.ofItemList(ImmutableList.of<MediaItem>(), params)
            }
            val episodes = mediaCatalog.episodes()
            val items = page(episodes.map { episodeBrowseItem(it) }, page, pageSize)
            LibraryResult.ofItemList(ImmutableList.copyOf(items), listStyleParams(params))
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> = future {
            when (val parsed = CatalogMediaId.parse(mediaId)) {
                is CatalogMediaId.Episode ->
                    mediaCatalog.episode(parsed.date)?.let { LibraryResult.ofItem(episodeBrowseItem(it), null) }
                        ?: LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
                is CatalogMediaId.Segment -> {
                    val seg = mediaCatalog.episode(parsed.date)?.segments?.firstOrNull { it.index == parsed.index }
                    seg?.let { LibraryResult.ofItem(segmentBrowseItem(it), null) }
                        ?: LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
                }
                null ->
                    if (mediaId == MEDIA_ID_ROOT) LibraryResult.ofItem(rootItem(), null)
                    else LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
            }
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> = future {
            val results = mediaCatalog.searchResults(query)
            session.notifySearchResultChanged(browser, query, results.size, params)
            LibraryResult.ofVoid()
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = future {
            val results = mediaCatalog.searchResults(query).map { episodeBrowseItem(it) }
            LibraryResult.ofItemList(ImmutableList.copyOf(page(results, page, pageSize)), listStyleParams(params))
        }
    }
}
