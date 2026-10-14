package com.nomnomsom.armstrongandgetty.data.repository

import com.nomnomsom.armstrongandgetty.data.model.DownloadState
import com.nomnomsom.armstrongandgetty.data.model.state
import com.nomnomsom.armstrongandgetty.download.DownloadGate
import com.nomnomsom.armstrongandgetty.download.WifiRequiredException
import com.nomnomsom.armstrongandgetty.testutil.BurstDay
import com.nomnomsom.armstrongandgetty.testutil.FakeDao
import com.nomnomsom.armstrongandgetty.testutil.FakeDeletionMarks
import com.nomnomsom.armstrongandgetty.testutil.FakeFeed
import com.nomnomsom.armstrongandgetty.testutil.FakeSegmentStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * "Download over Wi-Fi only" enforcement in PodcastRepository, the single choke point every
 * download path goes through. A block must change nothing: no state change (the day stays
 * NONE, never DOWNLOADING or ERROR), no transfer, and the feed refresh still records new hours.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WifiGateTest {

    private class Harness(today: LocalDate = LocalDate(2026, 7, 23)) {
        var allowed = false
        val dao = FakeDao()
        val feed = FakeFeed()
        val store = FakeSegmentStore()
        val marks = FakeDeletionMarks()
        val repo = PodcastRepository(
            dao, feed, store, marks,
            todayProvider = { today },
            downloadGate = DownloadGate { allowed }
        )

        suspend fun state(date: String = BurstDay.DATE) = dao.getDayByDate(date)!!.state

        /** Every downloadState value the day row takes, in order. */
        fun recordStates(scope: TestScope, date: String = BurstDay.DATE): List<String?> {
            val seen = mutableListOf<String?>()
            scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) {
                dao.days.collect { seen += it[date]?.downloadState }
            }
            return seen
        }
    }

    @Test
    fun blockedDownloadDayChangesNothing() = runTest {
        val h = Harness()
        h.feed.items = BurstDay.feedItems(BurstDay.hour1, BurstDay.hour2)
        h.repo.refreshFeed()
        h.marks.markDeleted(BurstDay.DATE)
        val states = h.recordStates(this)

        val result = h.repo.downloadDay(BurstDay.DATE)

        assertIs<WifiRequiredException>(result.exceptionOrNull())
        assertEquals(DownloadState.NONE, h.state())
        assertTrue(h.store.transferLog.isEmpty())
        assertTrue(h.marks.isDeleted(BurstDay.DATE), "the user-deletion mark is untouched")
        assertFalse(DownloadState.DOWNLOADING.value in states, "never DOWNLOADING: $states")
        assertFalse(DownloadState.ERROR.value in states, "never ERROR: $states")
    }

    /** Everything is already on disk: nothing to transfer, so the gate doesn't matter. */
    @Test
    fun blockedButAllOnDiskStillBecomesDownloaded() = runTest {
        val h = Harness()
        h.feed.items = BurstDay.feedItems(BurstDay.hour1, BurstDay.hour2)
        h.repo.refreshFeed()
        h.store.disk += BurstDay.DATE to 0
        h.store.disk += BurstDay.DATE to 1

        assertTrue(h.repo.downloadDay(BurstDay.DATE).isSuccess)
        assertEquals(DownloadState.DOWNLOADED, h.state())
        assertTrue(h.store.transferLog.isEmpty())
    }

    @Test
    fun blockedAppendRecordsNewHoursWithoutTransferring() = runTest {
        val h = Harness()
        h.allowed = true
        h.feed.items = BurstDay.feedItems(BurstDay.hour1)
        h.repo.refreshFeed()
        h.repo.downloadDay(BurstDay.DATE).getOrThrow()
        assertEquals(1, h.store.transferLog.size)

        // Off Wi-Fi with the setting on, hour 2 releases.
        h.allowed = false
        h.feed.items = BurstDay.feedItems(BurstDay.hour1, BurstDay.hour2)
        val result = h.repo.appendNewSegments(BurstDay.DATE)

        assertIs<WifiRequiredException>(result.exceptionOrNull())
        assertEquals(2, h.dao.getDayByDate(BurstDay.DATE)!!.segmentCount, "the refresh still records hour 2")
        assertEquals(1, h.store.transferLog.size, "no transfer while blocked")
        assertEquals(DownloadState.DOWNLOADED, h.state())

        // Back on Wi-Fi: the same call fetches the missing hour.
        h.allowed = true
        val append = h.repo.appendNewSegments(BurstDay.DATE).getOrThrow()
        assertEquals(listOf(1), append.appendedSegmentIndices)
        assertTrue(h.store.hasSegment(BurstDay.DATE, 1))
    }

    @Test
    fun blockedRetrySegmentDoesNotTransfer() = runTest {
        val h = Harness()
        h.feed.items = BurstDay.feedItems(BurstDay.hour1, BurstDay.hour2)
        h.repo.refreshFeed()

        val result = h.repo.retrySegment(BurstDay.DATE, 1)

        assertIs<WifiRequiredException>(result.exceptionOrNull())
        assertTrue(h.store.transferLog.isEmpty())
        assertEquals(DownloadState.NONE, h.state())

        h.allowed = true
        assertTrue(h.repo.retrySegment(BurstDay.DATE, 1).isSuccess)
        assertEquals(listOf(BurstDay.DATE to 1), h.store.transferLog)
    }

    @Test
    fun unblockedDownloadTransfers() = runTest {
        val h = Harness()
        h.feed.items = BurstDay.feedItems(BurstDay.hour1, BurstDay.hour2)
        h.repo.refreshFeed()
        assertTrue(h.repo.downloadDay(BurstDay.DATE).isFailure)

        h.allowed = true
        assertTrue(h.repo.downloadDay(BurstDay.DATE).isSuccess)
        assertEquals(DownloadState.DOWNLOADED, h.state())
        assertEquals(2, h.store.transferLog.size)
    }

    /** Wi-Fi dropped before the first file landed: every outcome is WifiRequired → NONE, not ERROR. */
    @Test
    fun allOutcomesBlockedFinalizesToNone() = runTest {
        val h = Harness()
        h.allowed = true
        h.feed.items = BurstDay.feedItems(BurstDay.hour1, BurstDay.hour2)
        h.repo.refreshFeed()
        h.store.wifiBlockedIndices += setOf(0, 1)

        val result = h.repo.downloadDay(BurstDay.DATE)

        assertIs<WifiRequiredException>(result.exceptionOrNull())
        assertEquals(DownloadState.NONE, h.state())
    }

    /** Some hours landed before Wi-Fi dropped: playable, so DOWNLOADED (as for any partial download). */
    @Test
    fun partiallyBlockedFinalizesToDownloaded() = runTest {
        val h = Harness()
        h.allowed = true
        h.feed.items = BurstDay.feedItems(BurstDay.hour1, BurstDay.hour2)
        h.repo.refreshFeed()
        h.store.wifiBlockedIndices += 1

        assertTrue(h.repo.downloadDay(BurstDay.DATE).isSuccess)
        assertEquals(DownloadState.DOWNLOADED, h.state())
        assertTrue(h.store.hasSegment(BurstDay.DATE, 0))
        assertFalse(h.store.hasSegment(BurstDay.DATE, 1))
    }

    /** A real failure mixed with a block, nothing on disk: still a failed download. */
    @Test
    fun blockedMixedWithRealFailureFinalizesToError() = runTest {
        val h = Harness()
        h.allowed = true
        h.feed.items = BurstDay.feedItems(BurstDay.hour1, BurstDay.hour2)
        h.repo.refreshFeed()
        h.store.wifiBlockedIndices += 0
        h.store.failIndices += 1

        val result = h.repo.downloadDay(BurstDay.DATE)

        assertTrue(result.isFailure)
        assertFalse(result.exceptionOrNull() is WifiRequiredException)
        assertEquals(DownloadState.ERROR, h.state())
    }

    /** No gate passed (every existing harness): downloads behave exactly as before the setting. */
    @Test
    fun defaultConstructorHasNoGate() = runTest {
        val dao = FakeDao()
        val feed = FakeFeed()
        val store = FakeSegmentStore()
        val repo = PodcastRepository(dao, feed, store, FakeDeletionMarks(), todayProvider = { LocalDate(2026, 7, 23) })
        feed.items = BurstDay.feedItems(BurstDay.hour1, BurstDay.hour2)
        repo.refreshFeed()

        assertTrue(repo.downloadDay(BurstDay.DATE).isSuccess)
        assertEquals(2, store.transferLog.size)
        assertEquals(DownloadState.DOWNLOADED, dao.getDayByDate(BurstDay.DATE)!!.state)
    }
}
