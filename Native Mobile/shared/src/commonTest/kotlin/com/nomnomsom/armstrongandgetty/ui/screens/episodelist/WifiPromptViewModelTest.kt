package com.nomnomsom.armstrongandgetty.ui.screens.episodelist

import com.nomnomsom.armstrongandgetty.analytics.AnalyticsEvents
import com.nomnomsom.armstrongandgetty.data.model.DownloadState
import com.nomnomsom.armstrongandgetty.data.model.state
import com.nomnomsom.armstrongandgetty.data.repository.PodcastRepository
import com.nomnomsom.armstrongandgetty.download.DownloadPolicy
import com.nomnomsom.armstrongandgetty.download.DownloadSettings
import com.nomnomsom.armstrongandgetty.download.NetworkKind
import com.nomnomsom.armstrongandgetty.media.PlaybackController
import com.nomnomsom.armstrongandgetty.review.SessionHealth
import com.nomnomsom.armstrongandgetty.testutil.BurstDay
import com.nomnomsom.armstrongandgetty.testutil.FakeDao
import com.nomnomsom.armstrongandgetty.testutil.FakeDeletionMarks
import com.nomnomsom.armstrongandgetty.testutil.FakeFeed
import com.nomnomsom.armstrongandgetty.testutil.FakeNetworkStatus
import com.nomnomsom.armstrongandgetty.testutil.FakePlatformPlayer
import com.nomnomsom.armstrongandgetty.testutil.FakeSegmentStore
import com.nomnomsom.armstrongandgetty.testutil.RecordingAnalytics
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The manual-tap side of "Download over Wi-Fi only" in EpisodeListViewModel: which taps get
 * the dialog, what it offers, and what "Turn Off & Download" does. The day is marked
 * user-deleted up front so the app-open auto-download pass leaves it to the manual tap.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WifiPromptViewModelTest {

    private class Harness(wifiOnly: Boolean, kind: NetworkKind) {
        val settings = DownloadSettings(MapSettings()).apply { setWifiOnly(wifiOnly) }
        val network = FakeNetworkStatus(kind)
        val policy = DownloadPolicy(settings, network)
        val dao = FakeDao()
        val feed = FakeFeed().apply { items = BurstDay.feedItems(BurstDay.hour1, BurstDay.hour2) }
        val store = FakeSegmentStore()
        val marks = FakeDeletionMarks().apply { markDeleted(BurstDay.DATE) }
        val analytics = RecordingAnalytics()
        val repo = PodcastRepository(
            dao, feed, store, marks,
            todayProvider = { LocalDate(2026, 7, 23) },
            downloadGate = policy
        )
        val vm = EpisodeListViewModel(
            repo, PlaybackController(FakePlatformPlayer()), analytics, SessionHealth(), policy, settings
        ).also {
            // Stop the 500 ms position poll so advanceUntilIdle() terminates.
            it.playbackController.disconnect()
        }

        suspend fun state() = dao.getDayByDate(BurstDay.DATE)!!.state
        val prompt get() = vm.wifiRequiredPrompt.value
        fun waitingSources() = analytics.named(AnalyticsEvents.DOWNLOAD_WAITING_WIFI).map { it[AnalyticsEvents.PARAM_SOURCE] }
    }

    private fun vmTest(wifiOnly: Boolean, kind: NetworkKind, body: suspend TestScope.(Harness) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val h = Harness(wifiOnly, kind)
            advanceUntilIdle() // init: refresh + auto-download pass
            body(h)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /** The default: a tap on mobile data downloads as before, with no dialog and no network read. */
    @Test
    fun settingOffDownloadsOnMobileDataWithoutReadingTheNetwork() = vmTest(false, NetworkKind.METERED) { h ->
        h.vm.downloadDay(BurstDay.DATE)
        advanceUntilIdle()

        assertNull(h.prompt)
        assertEquals(DownloadState.DOWNLOADED, h.state())
        assertEquals(2, h.store.transferLog.size)
        assertEquals(0, h.network.totalCalls)
        assertTrue(h.waitingSources().isEmpty())
    }

    @Test
    fun offlineTapNeverOffersTurnOff() = vmTest(true, NetworkKind.OFFLINE) { h ->
        h.vm.downloadDay(BurstDay.DATE)
        advanceUntilIdle()

        val prompt = assertNotNull(h.prompt)
        assertEquals(NetworkKind.OFFLINE, prompt.network)
        assertFalse(prompt.offersTurnOff)
        assertFalse("Turn it off" in wifiRequiredCopy(prompt).body)

        // Even if called, it must not give up the listener's setting for a download that would fail.
        h.vm.turnOffWifiOnlyAndRetry()
        advanceUntilIdle()
        assertTrue(h.settings.wifiOnly.value)
        assertNull(h.prompt)
        assertTrue(h.store.transferLog.isEmpty())
        assertTrue(h.marks.isDeleted(BurstDay.DATE), "a blocked tap changes nothing")
    }

    @Test
    fun unknownReadingDoesNotOfferTurnOff() = vmTest(true, NetworkKind.UNKNOWN) { h ->
        h.vm.downloadDay(BurstDay.DATE)
        advanceUntilIdle()

        val prompt = assertNotNull(h.prompt)
        assertEquals(NetworkKind.UNKNOWN, prompt.network)
        assertFalse(prompt.offersTurnOff)
    }

    @Test
    fun meteredTapOffersTurnOffAndRedoesTheDownload() = vmTest(true, NetworkKind.METERED) { h ->
        h.vm.downloadDay(BurstDay.DATE)
        advanceUntilIdle()

        val prompt = assertNotNull(h.prompt)
        assertEquals(NetworkKind.METERED, prompt.network)
        assertTrue(prompt.offersTurnOff)
        assertEquals(DownloadState.NONE, h.state())
        assertEquals(listOf<Any?>("manual"), h.waitingSources())

        h.vm.turnOffWifiOnlyAndRetry()
        advanceUntilIdle()

        assertFalse(h.settings.wifiOnly.value)
        assertNull(h.prompt)
        assertEquals(DownloadState.DOWNLOADED, h.state())
        assertEquals(2, h.store.transferLog.size)
    }

    /** Turning the setting on while a manual download runs must not nag to turn it back off. */
    @Test
    fun settingTurnedOnMidDownloadStaysSilent() = vmTest(false, NetworkKind.METERED) { h ->
        val gate = CompletableDeferred<Unit>()
        h.store.transferGate = gate
        h.vm.downloadDay(BurstDay.DATE)
        advanceUntilIdle() // hour 1 is mid-transfer

        h.settings.setWifiOnly(true)
        h.store.wifiBlockedIndices += setOf(0, 1) // the downloader's backstop stops both
        gate.complete(Unit)
        advanceUntilIdle()

        assertNull(h.prompt)
        assertEquals(DownloadState.NONE, h.state())
        assertEquals(listOf<Any?>("setting_changed"), h.waitingSources())
    }

    /** Hour 1 landed, then Wi-Fi went away: not "completed" — the dialog says the rest is waiting. */
    @Test
    fun partialManualDownloadPromptsForTheRest() = vmTest(true, NetworkKind.UNMETERED) { h ->
        val gate = CompletableDeferred<Unit>()
        h.store.transferGate = gate
        h.vm.downloadDay(BurstDay.DATE)
        advanceUntilIdle() // hour 1 is mid-transfer on Wi-Fi

        h.network.kind = NetworkKind.METERED
        h.store.wifiBlockedIndices += 1
        gate.complete(Unit)
        advanceUntilIdle()

        val prompt = assertNotNull(h.prompt)
        assertTrue(prompt.partial)
        assertEquals(NetworkKind.METERED, prompt.network)
        assertTrue("the rest of this episode" in wifiRequiredCopy(prompt).body)
        assertTrue(h.analytics.named(AnalyticsEvents.DOWNLOAD_COMPLETED).isEmpty())
        assertEquals(DownloadState.DOWNLOADED, h.state())

        // "Turn Off & Download" fetches the rest.
        h.store.wifiBlockedIndices.clear()
        h.vm.turnOffWifiOnlyAndRetry()
        advanceUntilIdle()
        assertTrue(h.store.hasSegment(BurstDay.DATE, 0))
        assertTrue(h.store.hasSegment(BurstDay.DATE, 1))
        assertNull(h.prompt)
    }

    @Test
    fun copyUsesThePlatformTermAndQuotesTheSettingName() {
        val body = wifiRequiredCopy(WifiRequiredPrompt("2026-07-23", network = NetworkKind.METERED), "cellular data").body
        assertTrue("cellular data" in body)
        assertTrue("\"Download over Wi-Fi only\"" in body)
    }
}
