package com.nomnomsom.armstrongandgetty.data.repository

import com.nomnomsom.armstrongandgetty.data.model.DownloadState
import com.nomnomsom.armstrongandgetty.data.model.state
import com.nomnomsom.armstrongandgetty.testutil.BurstDay
import com.nomnomsom.armstrongandgetty.testutil.FakeDao
import com.nomnomsom.armstrongandgetty.testutil.FakeDeletionMarks
import com.nomnomsom.armstrongandgetty.testutil.FakeFeed
import com.nomnomsom.armstrongandgetty.testutil.FakeSegmentStore
import com.nomnomsom.armstrongandgetty.testutil.WeekendTieDay
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * DESIRED-BEHAVIOR tests for defects found in the episode-creation engine.
 *
 * Every test here encodes what the engine SHOULD do in a scenario taken from the live
 * feed's real publication patterns. A failing test in this file is a confirmed bug —
 * see the engine report for the analysis and recommended fix of each. When the fixes
 * land, this file must pass and stays as the regression suite.
 */
class EpisodeEngineBugTest {

    private class Harness(today: LocalDate = LocalDate(2026, 7, 23)) {
        val dao = FakeDao()
        val feed = FakeFeed()
        val store = FakeSegmentStore()
        val marks = FakeDeletionMarks()
        val repo = PodcastRepository(dao, feed, store, marks, todayProvider = { today })
    }

    /**
     * BUG 1 — the "requires a manual download" failure.
     *
     * Timeline (happens every single morning on Android, and on iOS whenever the app is
     * opened between a segment's release and the player poll):
     *   1. Hour 1 is downloaded; user is listening.
     *   2. Hour 2 releases.
     *   3. ANY feed refresh runs (app-open refresh, pull-to-refresh, the Android worker,
     *      iOS background refresh). It updates the day row to 2 segments but downloads
     *      nothing — refreshFeed never downloads.
     *   4. appendNewSegments runs next (player poll / worker / VM checkForNewSegments).
     *      It compares the row's segment count before vs after its own internal refresh,
     *      sees no growth (the growth was already recorded in step 3), and returns
     *      without downloading anything. Hour 2's file never arrives.
     *
     * Desired: appendNewSegments downloads whatever known segments are missing from disk,
     * regardless of which refresh first recorded them.
     */
    @Test
    fun bug1_appendDownloadsSegmentsAlreadyRecordedByAnEarlierRefresh() = runTest {
        val h = Harness()
        h.feed.items = BurstDay.feedItems(BurstDay.hour1)
        h.repo.refreshFeed()
        h.repo.downloadDay(BurstDay.DATE)

        // Hour 2 releases; an ordinary refresh (not an append) records it first.
        h.feed.items = BurstDay.feedItems(BurstDay.hour1, BurstDay.hour2)
        h.repo.refreshFeed()

        // Now the append path runs — it must fetch the missing file.
        val append = h.repo.appendNewSegments(BurstDay.DATE).getOrThrow()

        assertTrue(
            h.store.hasSegment(BurstDay.DATE, 1),
            "hour 2 was recorded in the day row but its file was never downloaded"
        )
        assertEquals(listOf(1), append.appendedSegmentIndices)
    }

    /**
     * BUG 1b — same defect, reached through a failed first attempt: the append's own
     * download of hour 2 fails once (flaky network). The row now knows 2 segments, so
     * every subsequent poll no-ops and the file is never retried.
     */
    @Test
    fun bug1b_appendRetriesASegmentWhoseFirstDownloadFailed() = runTest {
        val h = Harness()
        h.feed.items = BurstDay.feedItems(BurstDay.hour1)
        h.repo.refreshFeed()
        h.repo.downloadDay(BurstDay.DATE)

        h.feed.items = BurstDay.feedItems(BurstDay.hour1, BurstDay.hour2)
        h.store.failIndices += 1
        h.repo.appendNewSegments(BurstDay.DATE) // fails — network blip

        h.store.failIndices.clear()
        h.repo.appendNewSegments(BurstDay.DATE) // next 2-minute poll tick

        assertTrue(
            h.store.hasSegment(BurstDay.DATE, 1),
            "poll after a transient failure must retry the missing segment"
        )
    }

    /**
     * BUG 2 — weekend shows play in the wrong order with wrong labels.
     *
     * Real feed data (2026-07-18, 2026-07-25): both weekend hours carry an IDENTICAL
     * pubDate and the feed lists Hour Two first. The engine sorts by pubDate only —
     * a stable sort keeps feed order — so Hour Two lands at index 0 and the positional
     * fallback then labels it "1" ("Hour Two" spelled out never matches the digit-only
     * regex). The user presses play and hears the second hour first, labelled "Hr 1".
     *
     * Desired: spelled-out hour markers are understood, and equal pubDates are
     * tie-broken by hour number.
     */
    @Test
    fun bug2_weekendHoursWithTiedPubDatesOrderByHourNumber() = runTest {
        val h = Harness(today = LocalDate(2026, 7, 26))
        h.feed.items = WeekendTieDay.feedOrder // [Hour Two, Hour One] — exactly as served

        h.repo.refreshFeed()

        val day = h.dao.getDayByDate(WeekendTieDay.DATE)!!
        val segments = h.repo.parseSegments(day.segmentsJson)
        assertEquals(
            listOf(WeekendTieDay.hourOne.title, WeekendTieDay.hourTwo.title),
            segments.map { it.title },
            "weekend hours must play in broadcast order"
        )
        assertEquals(listOf("1", "2"), segments.map { it.hour })
    }

    /**
     * BUG 3 — a refresh that lands during a long download gets clobbered.
     *
     * downloadDay captures the day's segment list, then spends minutes downloading.
     * If a refresh discovers hour 2 meanwhile, finalizeDownload persists the STALE
     * captured list and wipes hour 2 back out of the row. (It usually reappears on a
     * later refresh, but the row is wrong in between, the UI flickers, and worker
     * diff logic sees phantom "new segments" again.)
     *
     * Desired: finalizing a download must not remove segments recorded since the
     * download started.
     */
    @Test
    fun bug3_downloadFinalizeKeepsSegmentsDiscoveredMidDownload() = runTest {
        val h = Harness()
        h.feed.items = BurstDay.feedItems(BurstDay.hour1)
        h.repo.refreshFeed()

        // Start downloading hour 1 but hold the transfer mid-flight.
        h.store.transferGate = CompletableDeferred()
        h.store.onTransferStarted = CompletableDeferred()
        val download = launch { h.repo.downloadDay(BurstDay.DATE) }
        h.store.onTransferStarted!!.await()

        // While the download is in flight, hour 2 releases and a refresh records it.
        h.feed.items = BurstDay.feedItems(BurstDay.hour1, BurstDay.hour2)
        h.repo.refreshFeed()
        assertEquals(2, h.dao.getDayByDate(BurstDay.DATE)!!.segmentCount)

        // Let the download finish and finalize.
        h.store.transferGate!!.complete(Unit)
        download.join()

        val day = h.dao.getDayByDate(BurstDay.DATE)!!
        assertEquals(
            2, day.segmentCount,
            "finalizeDownload reverted the row to the stale pre-download segment list"
        )
        assertEquals(DownloadState.DOWNLOADED, day.state)
    }
}
