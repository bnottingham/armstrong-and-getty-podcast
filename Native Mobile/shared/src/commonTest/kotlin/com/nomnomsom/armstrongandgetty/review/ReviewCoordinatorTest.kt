package com.nomnomsom.armstrongandgetty.review

import com.nomnomsom.armstrongandgetty.analytics.AnalyticsEvents
import com.nomnomsom.armstrongandgetty.analytics.AnalyticsTracker
import com.nomnomsom.armstrongandgetty.media.PlaybackState
import com.nomnomsom.armstrongandgetty.review.ReviewPolicy.DAY_MS
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewCoordinatorTest {

    private val zone = TimeZone.UTC
    private val baseMs = LocalDateTime(2026, 10, 1, 12, 0).toInstant(zone).toEpochMilliseconds()
    private val activeDay = "2026-10-01"

    // ---- fakes ---------------------------------------------------------------------------

    private class FakeEnvironment : ReviewEnvironment {
        override var autoPromptEnabled = true
        var version = "2.1.0"
        var crashed = false
        var casting = false
        override fun appVersion() = version
        override fun crashedOnPreviousRun() = crashed
        override fun isCasting() = casting
    }

    private class RecordingAnalytics : AnalyticsTracker {
        val events = mutableListOf<Pair<String, Map<String, Any>>>()
        override fun logEvent(name: String, params: Map<String, Any>) {
            events += name to params
        }
        override fun logScreen(screenName: String) {}
        override fun setUserProperty(name: String, value: String?) {}
        override fun recordError(message: String, throwable: Throwable?) {}
    }

    private class FakeLauncher(private val body: () -> ReviewLaunchResult = { ReviewLaunchResult.CALLED }) : ReviewLauncher {
        var calls = 0
        override suspend fun launch(): ReviewLaunchResult {
            calls++
            return body()
        }
    }

    private inner class Harness(val test: TestScope) {
        val settings = MapSettings()
        val store = ReviewStateStore(settings)
        val env = FakeEnvironment()
        val playback = MutableStateFlow(PlaybackState())
        val health = SessionHealth()
        val analytics = RecordingAnalytics()
        val clock = object : ReviewClock {
            override fun nowMs() = baseMs + test.testScheduler.currentTime
            override fun timeZone() = zone
        }
        val coordinator = ReviewCoordinator(
            store = store,
            environment = env,
            playbackState = playback,
            sessionHealth = health,
            analytics = analytics,
            clock = clock,
            scope = test.backgroundScope,
        )

        fun today() = ReviewPolicy.localDayKey(clock.nowMs(), zone)

        /** Long-time listener: first seen 30 days ago, today + 2 earlier days qualify. */
        fun seedEligible() {
            store.save(
                ReviewState(
                    firstSeenMs = clock.nowMs() - 30 * DAY_MS,
                    qualifyingDays = listOf("2026-09-28", "2026-09-30", today()),
                )
            )
        }

        fun startOn(screen: ReviewScreen = ReviewScreen.EPISODES, resumed: Boolean = true) {
            coordinator.start()
            coordinator.onScreen(screen, resumed)
            test.runCurrent()
        }

        fun play() {
            playback.value = playback.value.copy(isPlaying = true, currentDayDate = activeDay)
            test.runCurrent()
        }

        fun pause() {
            playback.value = playback.value.copy(isPlaying = false)
            test.runCurrent()
        }

        /** Ticks like PlaybackController (every 500 ms) for [ms], then stays playing. */
        fun tickPlaying(ms: Long) {
            var elapsed = 0L
            while (elapsed < ms) {
                test.advanceTimeBy(500)
                elapsed += 500
                playback.value = playback.value.copy(currentPositionMs = playback.value.currentPositionMs + 500)
                test.runCurrent()
            }
        }

        fun listen(ms: Long) {
            play()
            tickPlaying(ms)
            pause()
        }

        fun settle() {
            test.advanceTimeBy(ReviewPolicy.SETTLE_MS)
            test.runCurrent()
        }

        fun requests() = analytics.events.filter { it.first == AnalyticsEvents.IN_APP_REVIEW_REQUESTED }
    }

    private fun reviewTest(block: Harness.() -> Unit) = runTest { Harness(this).block() }

    // ---- listening credit ----------------------------------------------------------------

    @Test
    fun tenMinutesOnThreeDaysBuildsThreeQualifyingDays() = reviewTest {
        startOn(ReviewScreen.OTHER)
        repeat(3) { day ->
            listen(10 * 60_000L)
            assertEquals(day + 1, store.load().qualifyingDays.size, "after day ${day + 1}")
            test.advanceTimeBy(DAY_MS)
            test.runCurrent()
        }
        assertEquals(listOf("2026-10-01", "2026-10-02", "2026-10-03"), store.load().qualifyingDays)
    }

    @Test
    fun nineMinutesDoesNotQualify() = reviewTest {
        startOn(ReviewScreen.OTHER)
        listen(9 * 60_000L)
        val state = store.load()
        assertEquals(emptyList(), state.qualifyingDays)
        assertEquals(9 * 60_000L, state.listenDayMs)
    }

    @Test
    fun aLongGapBetweenPlayingEmissionsCreditsAtMostTwoSeconds() = reviewTest {
        startOn(ReviewScreen.OTHER)
        play()
        test.advanceTimeBy(60_000)
        playback.value = playback.value.copy(currentPositionMs = 60_000)
        test.runCurrent()
        pause()
        assertEquals(ReviewPolicy.MAX_TICK_CREDIT_MS, store.load().listenDayMs)
    }

    @Test
    fun creditBeforeLocalMidnightStaysOnThatDay() = reviewTest {
        // 12:00 → 23:59:00, then listen two minutes across midnight.
        test.advanceTimeBy(11 * 3_600_000L + 59 * 60_000L)
        startOn(ReviewScreen.OTHER)
        listen(2 * 60_000L)
        val state = store.load()
        assertEquals("2026-10-02", state.listenDayKey)
        // Only the ~60 s after midnight (each interval counts toward the day it ends on).
        assertTrue(state.listenDayMs in 60_000L..61_000L, "listenDayMs=${state.listenDayMs}")
    }

    @Test
    fun startTwiceRunsOneCollectorAndRecordsFirstSeenOnce() = reviewTest {
        coordinator.start()
        val firstSeen = store.load().firstSeenMs
        assertNotNull(firstSeen)
        test.advanceTimeBy(5_000)
        coordinator.start()
        test.runCurrent()
        assertEquals(firstSeen, store.load().firstSeenMs)

        listen(5 * 60_000L)
        assertEquals(5 * 60_000L, store.load().listenDayMs) // not doubled
    }

    // ---- the happy path ----------------------------------------------------------------

    @Test
    fun pausedOnListRequestsOnceAfterSettleAndPersistsFirst() = reviewTest {
        seedEligible()
        var countSeenInsideLaunch = -1
        var lastRequestSeenInsideLaunch: Long? = null
        val launcher = FakeLauncher {
            val persisted = store.load()
            countSeenInsideLaunch = persisted.requestCount
            lastRequestSeenInsideLaunch = persisted.lastRequestMs
            ReviewLaunchResult.CALLED
        }
        coordinator.attach(launcher)
        startOn()

        play()
        tickPlaying(5_000)
        pause()
        val pausedAt = clock.nowMs()

        test.advanceTimeBy(ReviewPolicy.SETTLE_MS - 1)
        test.runCurrent()
        assertEquals(0, launcher.calls, "must wait out the settle delay")

        test.advanceTimeBy(1)
        test.runCurrent()
        assertEquals(1, launcher.calls)

        // Persist-first: the attempt was already on disk when the platform was called.
        assertEquals(1, countSeenInsideLaunch)
        assertEquals(pausedAt + ReviewPolicy.SETTLE_MS, lastRequestSeenInsideLaunch)

        val stored = store.load()
        assertEquals(1, stored.requestCount)
        assertEquals("2.1.0", stored.lastRequestVersion)
        assertEquals(emptyList(), stored.qualifyingDays)

        val (_, params) = requests().single()
        assertEquals("paused_on_list", params[AnalyticsEvents.PARAM_MOMENT])
        assertEquals(1, params[AnalyticsEvents.PARAM_REQUEST_NUMBER])
        assertEquals(3, params[AnalyticsEvents.PARAM_QUALIFYING_DAYS])
        assertEquals("called", params[AnalyticsEvents.PARAM_RESULT])
    }

    @Test
    fun leftPlayerRequests() = reviewTest {
        seedEligible()
        playback.value = PlaybackState(currentDayDate = activeDay) // loaded, paused
        val launcher = FakeLauncher()
        coordinator.attach(launcher)
        startOn(ReviewScreen.PLAYER)

        coordinator.onScreen(ReviewScreen.EPISODES, resumed = true)
        assertTrue(coordinator.hasPendingMoment)
        settle()
        assertEquals(1, launcher.calls)
        assertEquals("left_player", requests().single().second[AnalyticsEvents.PARAM_MOMENT])
    }

    @Test
    fun aSecondMomentInTheSameProcessNeverLaunchesAgain() = reviewTest {
        seedEligible()
        val launcher = FakeLauncher()
        coordinator.attach(launcher)
        startOn()
        play(); pause(); settle()
        assertEquals(1, launcher.calls)

        // Both moments again: ALREADY_THIS_PROCESS (gate 2) stops them before anything else.
        play(); pause(); settle()
        coordinator.onScreen(ReviewScreen.PLAYER, true)
        coordinator.onScreen(ReviewScreen.EPISODES, true)
        settle()
        assertEquals(1, launcher.calls)
        assertEquals(1, store.load().requestCount)
    }

    // ---- live re-checks during the settle -------------------------------------------------

    @Test
    fun resumingPlaybackDuringSettleCancelsTheAsk() = reviewTest {
        seedEligible()
        val launcher = FakeLauncher()
        coordinator.attach(launcher)
        startOn()
        play(); pause()
        test.advanceTimeBy(1_000)
        play()
        settle()
        assertEquals(0, launcher.calls)
        assertEquals(0, store.load().requestCount)
    }

    @Test
    fun leavingForegroundDuringSettleCancelsTheAsk() = reviewTest {
        seedEligible()
        val launcher = FakeLauncher()
        coordinator.attach(launcher)
        startOn()
        play(); pause()
        test.advanceTimeBy(1_000)
        coordinator.onScreen(ReviewScreen.EPISODES, resumed = false)
        settle()
        assertEquals(0, launcher.calls)
        assertEquals(0, store.load().requestCount)
    }

    @Test
    fun aSessionFailureSuppressesTheAsk() = reviewTest {
        seedEligible()
        val launcher = FakeLauncher()
        coordinator.attach(launcher)
        startOn()
        health.reportFailure("download")
        play(); pause(); settle()
        assertEquals(0, launcher.calls)
    }

    @Test
    fun castingSuppressesTheAsk() = reviewTest {
        seedEligible()
        env.casting = true
        val launcher = FakeLauncher()
        coordinator.attach(launcher)
        startOn()
        play(); pause(); settle()
        assertEquals(0, launcher.calls)
    }

    @Test
    fun noLauncherSpendsNoAttempt() = reviewTest {
        seedEligible()
        startOn()
        play(); pause(); settle()
        assertEquals(0, store.load().requestCount)
        assertTrue(requests().isEmpty())
    }

    @Test
    fun detachOnlyClearsTheSameLauncher() = reviewTest {
        seedEligible()
        val old = FakeLauncher()
        val current = FakeLauncher()
        coordinator.attach(old)
        coordinator.attach(current) // Activity recreated: new launcher attached first…
        coordinator.detach(old) // …then the old composition disposes.
        startOn()
        play(); pause(); settle()
        assertEquals(0, old.calls)
        assertEquals(1, current.calls)
    }

    // ---- moments only fire at natural pauses -----------------------------------------------

    @Test
    fun pauseOffTheEpisodesListOrInBackgroundIsNotAMoment() = reviewTest {
        seedEligible()
        val launcher = FakeLauncher()
        coordinator.attach(launcher)
        startOn(ReviewScreen.PLAYER)

        for ((screen, resumed) in listOf(
            ReviewScreen.PLAYER to true,
            ReviewScreen.ABOUT to true,
            ReviewScreen.EPISODES to false, // lock screen / Control Center / Auto pause
        )) {
            coordinator.onScreen(screen, resumed)
            test.runCurrent()
            play(); pause()
            assertFalse(coordinator.hasPendingMoment, "$screen resumed=$resumed")
        }
        assertEquals(0, launcher.calls)
    }

    @Test
    fun foregroundingOnTheSameScreenIsNotAMoment() = reviewTest {
        seedEligible()
        playback.value = PlaybackState(currentDayDate = activeDay)
        val launcher = FakeLauncher()
        coordinator.attach(launcher)
        startOn(ReviewScreen.EPISODES, resumed = false)
        coordinator.onScreen(ReviewScreen.EPISODES, resumed = true)
        assertFalse(coordinator.hasPendingMoment)
        settle()
        assertEquals(0, launcher.calls)
    }

    @Test
    fun aRecreatedHostOpeningOnEpisodesIsNotLeftPlayer() = reviewTest {
        seedEligible()
        playback.value = PlaybackState(currentDayDate = activeDay)
        val launcher = FakeLauncher()
        coordinator.attach(launcher)
        startOn(ReviewScreen.PLAYER)
        // ReviewPromptHost.onDispose resets to OTHER; the next host starts on Episodes.
        coordinator.onScreen(ReviewScreen.OTHER, resumed = false)
        coordinator.onScreen(ReviewScreen.EPISODES, resumed = true)
        assertFalse(coordinator.hasPendingMoment)
        settle()
        assertEquals(0, launcher.calls)
    }

    // ---- crash, kill switch, failing launcher ----------------------------------------------

    @Test
    fun aCrashOnThePreviousRunHoldsThePrompt() = reviewTest {
        seedEligible()
        env.crashed = true
        val launcher = FakeLauncher()
        coordinator.attach(launcher)
        startOn()
        assertEquals(clock.nowMs(), store.load().lastCrashMs)
        play(); pause(); settle()
        assertEquals(0, launcher.calls)
        assertEquals(0, store.load().requestCount)
    }

    @Test
    fun disabledAutoPromptStillCountsListeningDays() = reviewTest {
        env.autoPromptEnabled = false
        store.save(ReviewState(firstSeenMs = clock.nowMs() - 30 * DAY_MS, qualifyingDays = listOf("2026-09-28", "2026-09-30")))
        val launcher = FakeLauncher()
        coordinator.attach(launcher)
        startOn()
        listen(10 * 60_000L)
        settle()
        assertEquals(0, launcher.calls)
        assertEquals(listOf("2026-09-28", "2026-09-30", "2026-10-01"), store.load().qualifyingDays)
        assertEquals(0, store.load().requestCount)
    }

    @Test
    fun aThrowingLauncherCountsAsUnavailableAndStillSpendsTheAttempt() = reviewTest {
        seedEligible()
        val launcher = FakeLauncher { throw IllegalStateException("no Play Store") }
        coordinator.attach(launcher)
        startOn()
        play(); pause(); settle()
        assertEquals(1, launcher.calls)
        assertEquals(1, store.load().requestCount)
        assertEquals("unavailable", requests().single().second[AnalyticsEvents.PARAM_RESULT])
    }

    // ---- route mapping -------------------------------------------------------------------

    @Test
    fun routesMapToReviewScreens() {
        assertEquals(ReviewScreen.EPISODES, reviewScreenForRoute("episodes"))
        assertEquals(ReviewScreen.PLAYER, reviewScreenForRoute("player/{date}"))
        assertEquals(ReviewScreen.ABOUT, reviewScreenForRoute("about"))
        assertEquals(ReviewScreen.OTHER, reviewScreenForRoute(null))
        assertEquals(ReviewScreen.OTHER, reviewScreenForRoute("settings"))
    }
}
