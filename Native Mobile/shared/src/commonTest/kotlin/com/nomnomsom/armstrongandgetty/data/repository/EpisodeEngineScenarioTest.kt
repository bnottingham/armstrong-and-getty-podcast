package com.nomnomsom.armstrongandgetty.data.repository

import com.nomnomsom.armstrongandgetty.data.model.DownloadState
import com.nomnomsom.armstrongandgetty.data.model.state
import com.nomnomsom.armstrongandgetty.testutil.BurstDay
import com.nomnomsom.armstrongandgetty.testutil.FakeDao
import com.nomnomsom.armstrongandgetty.testutil.FakeDeletionMarks
import com.nomnomsom.armstrongandgetty.testutil.FakeFeed
import com.nomnomsom.armstrongandgetty.testutil.FakeSegmentStore
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * End-to-end scenarios for the episode-creation engine using real feed timelines
 * (2026-07-23: hour 1 at 7:45am PT, hours 2-4 + bonus in a burst at ~1pm PT).
 * Network and disk are faked; the repository, grouping, and state logic are real.
 */
class EpisodeEngineScenarioTest {

    private class Harness(today: LocalDate = LocalDate(2026, 7, 23)) {
        val dao = FakeDao()
        val feed = FakeFeed()
        val store = FakeSegmentStore()
        val marks = FakeDeletionMarks()
        val repo = PodcastRepository(dao, feed, store, marks, todayProvider = { today })
    }

    // -- Morning: only hour 1 exists --

    @Test
    fun firstRefreshCreatesIncompleteDayWithOneSegment() = runTest {
        val h = Harness()
        h.feed.items = BurstDay.feedItems(BurstDay.hour1)

        val result = h.repo.refreshFeed()

        assertEquals(BurstDay.DATE, result.getOrThrow())
        val day = h.dao.getDayByDate(BurstDay.DATE)!!
        assertEquals(1, day.segmentCount)
        assertFalse(day.isComplete)
        assertEquals(DownloadState.NONE, day.state)
        assertEquals("A&G — Jul 23, 2026", day.title)
    }

    @Test
    fun downloadDayFetchesAllKnownSegments() = runTest {
        val h = Harness()
        h.feed.items = BurstDay.feedItems(BurstDay.hour1)
        h.repo.refreshFeed()

        val result = h.repo.downloadDay(BurstDay.DATE)

        assertTrue(result.isSuccess)
        assertTrue(h.store.hasSegment(BurstDay.DATE, 0))
        assertEquals(DownloadState.DOWNLOADED, h.dao.getDayByDate(BurstDay.DATE)!!.state)
    }

    // -- The reported user story: listening to hour 1 when hour 2 drops --

    @Test
    fun liveAppendDownloadsNewlyReleasedSegment() = runTest {
        val h = Harness()
        h.feed.items = BurstDay.feedItems(BurstDay.hour1)
        h.repo.refreshFeed()
        h.repo.downloadDay(BurstDay.DATE)

        // Hour 2 releases while the user listens; the player poll calls appendNewSegments
        // (no other refresh has run in between).
        h.feed.items = BurstDay.feedItems(BurstDay.hour1, BurstDay.hour2)
        val result = h.repo.appendNewSegments(BurstDay.DATE)

        val append = result.getOrThrow()
        assertEquals(listOf(1), append.appendedSegmentIndices)
        assertTrue(h.store.hasSegment(BurstDay.DATE, 1))
        val day = h.dao.getDayByDate(BurstDay.DATE)!!
        assertEquals(2, day.segmentCount)
        assertEquals(DownloadState.DOWNLOADED, day.state)
    }

    @Test
    fun liveAppendWholeBurstAtOnce() = runTest {
        val h = Harness()
        h.feed.items = BurstDay.feedItems(BurstDay.hour1)
        h.repo.refreshFeed()
        h.repo.downloadDay(BurstDay.DATE)

        h.feed.items = BurstDay.feedItems(*BurstDay.all.toTypedArray())
        val append = h.repo.appendNewSegments(BurstDay.DATE).getOrThrow()

        assertEquals(listOf(1, 2, 3, 4), append.appendedSegmentIndices)
        assertTrue(h.store.hasAllSegments(BurstDay.DATE, 5))
        val day = h.dao.getDayByDate(BurstDay.DATE)!!
        assertEquals(5, day.segmentCount)
        assertTrue(day.isComplete)
        // Hour 1's slot (and its file) must not have moved.
        val segments = h.repo.parseSegments(day.segmentsJson)
        assertEquals(BurstDay.hour1.title, segments[0].title)
    }

    @Test
    fun appendWithNoNewContentIsANoop() = runTest {
        val h = Harness()
        h.feed.items = BurstDay.feedItems(BurstDay.hour1)
        h.repo.refreshFeed()
        h.repo.downloadDay(BurstDay.DATE)

        val append = h.repo.appendNewSegments(BurstDay.DATE).getOrThrow()

        assertEquals(emptyList(), append.appendedSegmentIndices)
        assertEquals(1, append.previousSegmentCount)
    }

    // -- Failure handling --

    @Test
    fun partialDownloadFailureStillMarksDayDownloaded() = runTest {
        val h = Harness()
        h.feed.items = BurstDay.feedItems(BurstDay.hour1, BurstDay.hour2)
        h.repo.refreshFeed()
        h.store.failIndices += 1

        val result = h.repo.downloadDay(BurstDay.DATE)

        // Any-on-disk = playable; missing files surface per-segment retry UI.
        assertTrue(result.isSuccess)
        assertTrue(h.store.hasSegment(BurstDay.DATE, 0))
        assertFalse(h.store.hasSegment(BurstDay.DATE, 1))
        assertEquals(DownloadState.DOWNLOADED, h.dao.getDayByDate(BurstDay.DATE)!!.state)
    }

    @Test
    fun totalDownloadFailureMarksError() = runTest {
        val h = Harness()
        h.feed.items = BurstDay.feedItems(BurstDay.hour1)
        h.repo.refreshFeed()
        h.store.failIndices += 0

        val result = h.repo.downloadDay(BurstDay.DATE)

        assertTrue(result.isFailure)
        assertEquals(DownloadState.ERROR, h.dao.getDayByDate(BurstDay.DATE)!!.state)
    }

    @Test
    fun retrySegmentRecoversASingleMissingFile() = runTest {
        val h = Harness()
        h.feed.items = BurstDay.feedItems(BurstDay.hour1, BurstDay.hour2)
        h.repo.refreshFeed()
        h.store.failIndices += 1
        h.repo.downloadDay(BurstDay.DATE)

        h.store.failIndices.clear()
        val result = h.repo.retrySegment(BurstDay.DATE, 1)

        assertTrue(result.isSuccess)
        assertTrue(h.store.hasAllSegments(BurstDay.DATE, 2))
    }

    // -- Refresh stability across the day --

    @Test
    fun repeatedRefreshesKeepSegmentOrderAndDownloadedMetadata() = runTest {
        val h = Harness()
        h.feed.items = BurstDay.feedItems(BurstDay.hour1)
        h.repo.refreshFeed()
        h.repo.downloadDay(BurstDay.DATE)

        h.feed.items = BurstDay.feedItems(*BurstDay.all.toTypedArray())
        repeat(3) { h.repo.refreshFeed() }

        val day = h.dao.getDayByDate(BurstDay.DATE)!!
        val segments = h.repo.parseSegments(day.segmentsJson)
        assertEquals(5, segments.size)
        assertEquals(BurstDay.hour1.title, segments[0].title)
        // Hour 1's measured duration survives every refresh.
        assertTrue(segments[0].actualDurationMs > 0)
        assertEquals(DownloadState.DOWNLOADED, day.state)
    }

    @Test
    fun deleteDayClearsFilesAndMarksUserIntent() = runTest {
        val h = Harness()
        h.feed.items = BurstDay.feedItems(BurstDay.hour1)
        h.repo.refreshFeed()
        h.repo.downloadDay(BurstDay.DATE)

        h.repo.deleteDay(BurstDay.DATE)

        assertFalse(h.store.hasSegment(BurstDay.DATE, 0))
        assertEquals(DownloadState.NONE, h.dao.getDayByDate(BurstDay.DATE)!!.state)
        assertTrue(h.repo.isUserDeleted(BurstDay.DATE))
    }
}
