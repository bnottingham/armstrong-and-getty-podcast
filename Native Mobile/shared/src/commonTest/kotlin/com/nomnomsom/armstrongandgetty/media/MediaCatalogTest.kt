package com.nomnomsom.armstrongandgetty.media

import com.nomnomsom.armstrongandgetty.data.repository.PodcastRepository
import com.nomnomsom.armstrongandgetty.testutil.BurstDay
import com.nomnomsom.armstrongandgetty.testutil.FakeDao
import com.nomnomsom.armstrongandgetty.testutil.FakeDeletionMarks
import com.nomnomsom.armstrongandgetty.testutil.FakeFeed
import com.nomnomsom.armstrongandgetty.testutil.FakeSegmentStore
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [MediaCatalog] — the read model behind Android Auto's browse tree, voice search,
 * and playback resumption. The play requests these back must ALWAYS resolve to a playlist with
 * real URIs (a URI-less MediaItem crashes ExoPlayer and took down the app in the Play review).
 */
class MediaCatalogTest {

    private class Harness(today: LocalDate = LocalDate(2026, 7, 23)) {
        val dao = FakeDao()
        val feed = FakeFeed()
        val store = FakeSegmentStore()
        val marks = FakeDeletionMarks()
        val repo = PodcastRepository(dao, feed, store, marks, todayProvider = { today })
        val catalog = MediaCatalog(repo)

        suspend fun seedFullDay() {
            feed.items = BurstDay.feedItems(*BurstDay.all.toTypedArray())
            repo.refreshFeed()
            repo.downloadDay(BurstDay.DATE)
        }

        /** A day known in the feed but not downloaded — should still stream in the car. */
        suspend fun seedUndownloadedDay() {
            feed.items = BurstDay.feedItems(*BurstDay.all.toTypedArray())
            repo.refreshFeed()
        }
    }

    // ---- media id round-tripping ----------------------------------------------------

    @Test
    fun episodeAndSegmentMediaIdsParseBack() {
        val ep = CatalogMediaId.parse(episodeMediaId("2026-07-23"))
        assertEquals(CatalogMediaId.Episode("2026-07-23"), ep)

        val seg = CatalogMediaId.parse(segmentMediaId("2026-07-23", 2))
        assertEquals(CatalogMediaId.Segment("2026-07-23", 2), seg)

        assertNull(CatalogMediaId.parse(null))
        assertNull(CatalogMediaId.parse(""))
        assertNull(CatalogMediaId.parse("garbage"))
        assertNull(CatalogMediaId.parse(MEDIA_ID_ROOT))
    }

    // ---- browse tree ----------------------------------------------------------------

    @Test
    fun episodesListsDownloadedDayWithSegments() = runTest {
        val h = Harness()
        h.seedFullDay()

        val episodes = h.catalog.episodes()
        assertEquals(1, episodes.size)
        val ep = episodes.first()
        assertEquals(BurstDay.DATE, ep.date)
        assertEquals(5, ep.segments.size)
        assertTrue(ep.isFullyDownloaded)
        assertTrue(ep.segments.all { it.isDownloaded && it.isPlayable })
    }

    @Test
    fun undownloadedDayIsStillPlayableViaRemoteUrl() = runTest {
        val h = Harness()
        h.seedUndownloadedDay()

        val ep = h.catalog.latest()
        assertNotNull(ep)
        assertTrue(ep.segments.isNotEmpty())
        assertTrue(ep.segments.none { it.isDownloaded })
        assertTrue(ep.segments.all { it.isPlayable }) // remoteUrl carries them
        assertTrue(ep.subtitle.contains("Streams"))
    }

    @Test
    fun emptyLibraryTriggersFeedRefresh() = runTest {
        val h = Harness()
        h.feed.items = BurstDay.feedItems(*BurstDay.all.toTypedArray())
        // Nothing in the DB yet; episodes() must refresh the feed on demand.
        val episodes = h.catalog.episodes()
        assertEquals(1, episodes.size)
    }

    // ---- play-request resolution (the crash surface) --------------------------------

    @Test
    fun playbackForEpisodeIdResolvesToFullPlaylist() = runTest {
        val h = Harness()
        h.seedFullDay()

        val playback = h.catalog.playbackFor(episodeMediaId(BurstDay.DATE))
        assertNotNull(playback)
        assertEquals(5, playback.segments.size)
        assertEquals(0, playback.startSegmentIndex)
        assertTrue(playback.segments.all { it.isPlayable })
    }

    @Test
    fun playbackForSegmentIdStartsAtThatSegment() = runTest {
        val h = Harness()
        h.seedFullDay()

        val playback = h.catalog.playbackFor(segmentMediaId(BurstDay.DATE, 3))
        assertNotNull(playback)
        assertEquals(3, playback.startSegmentIndex)
        assertEquals(0L, playback.startPositionInSegmentMs)
    }

    @Test
    fun playbackForUnknownIdIsNull() = runTest {
        val h = Harness()
        h.seedFullDay()
        assertNull(h.catalog.playbackFor("episode:1999-01-01"))
        assertNull(h.catalog.playbackFor("garbage"))
        assertNull(h.catalog.playbackFor(null))
    }

    @Test
    fun resumePositionMapsIntoSegmentAndOffset() = runTest {
        val h = Harness()
        h.seedFullDay()
        // Each fake segment is 1 hour (3_600_000 ms). Resume 1.5h in → segment 1, +30min.
        h.repo.updateListenProgress(BurstDay.DATE, positionMs = 5_400_000L, isListened = false)

        val ep = h.catalog.episode(BurstDay.DATE)!!
        val playback = h.catalog.playbackForEpisode(ep)
        assertNotNull(playback)
        assertEquals(1, playback.startSegmentIndex)
        assertEquals(1_800_000L, playback.startPositionInSegmentMs)
    }

    @Test
    fun finishedEpisodeResumesFromStart() = runTest {
        val h = Harness()
        h.seedFullDay()
        h.repo.updateListenProgress(BurstDay.DATE, positionMs = 9_999_999L, isListened = true)

        val ep = h.catalog.episode(BurstDay.DATE)!!
        assertEquals(0L, ep.resumePositionMs)
        val playback = h.catalog.playbackForEpisode(ep)!!
        assertEquals(0, playback.startSegmentIndex)
        assertEquals(0L, playback.startPositionInSegmentMs)
    }

    // ---- voice / text search --------------------------------------------------------

    @Test
    fun emptySearchResumesNewestInProgress() = runTest {
        val h = Harness()
        h.seedFullDay()
        h.repo.updateListenProgress(BurstDay.DATE, positionMs = 1_000_000L, isListened = false)

        val ep = h.catalog.search("")
        assertNotNull(ep)
        assertEquals(BurstDay.DATE, ep.date)
    }

    @Test
    fun appNameSearchReturnsNewestEpisode() = runTest {
        val h = Harness()
        h.seedFullDay()
        val ep = h.catalog.search("Armstrong and Getty")
        assertNotNull(ep)
        assertEquals(BurstDay.DATE, ep.date)
    }

    @Test
    fun unknownQueryFallsBackToNewestNeverNull() = runTest {
        val h = Harness()
        h.seedFullDay()
        val ep = h.catalog.search("play something that does not exist at all")
        assertNotNull(ep) // a voice request must never dead-end
        assertEquals(BurstDay.DATE, ep.date)
    }

    @Test
    fun dateWordSearchMatchesTheRightDay() = runTest {
        val h = Harness()
        h.seedFullDay()
        assertEquals(BurstDay.DATE, h.catalog.search("play the July 23rd show")?.date)
        assertEquals(BurstDay.DATE, h.catalog.search("7/23")?.date)
        assertEquals(BurstDay.DATE, h.catalog.search("2026-07-23")?.date)
    }

    @Test
    fun topicWordSearchMatchesSegmentTitle() = runTest {
        val h = Harness()
        h.seedFullDay()
        // "Psychopaths" appears in hour 1's title.
        assertEquals(BurstDay.DATE, h.catalog.search("play the psychopaths bit")?.date)
    }

    @Test
    fun searchResultsAreEmptyQueryEqualsEverything() = runTest {
        val h = Harness()
        h.seedFullDay()
        assertEquals(h.catalog.episodes().map { it.date }, h.catalog.searchResults("").map { it.date })
    }
}
