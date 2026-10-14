package com.nomnomsom.armstrongandgetty.review

import com.nomnomsom.armstrongandgetty.analytics.AnalyticsEvents
import com.nomnomsom.armstrongandgetty.analytics.AnalyticsTracker
import com.nomnomsom.armstrongandgetty.media.PlaybackState
import com.nomnomsom.armstrongandgetty.review.ReviewPolicy.DAY_MS
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
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

    private val baseMs = LocalDateTime(2026, 10, 1, 12, 0).toInstant(TimeZone.UTC).toEpochMilliseconds()
    private val activeDay = "2026-10-01"

    private val losAngeles = TimeZone.of("America/Los_Angeles")

    /** 16:50 PDT on Oct 1 = 23:50 UTC: ten minutes from here cross UTC midnight, not local. */
    private val laEveningMs = LocalDateTime(2026, 10, 1, 16, 50).toInstant(losAngeles).toEpochMilliseconds()

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

    private class FakeLauncher(
        var presentable: Boolean = true,
        private val body: () -> ReviewLaunchResult = { ReviewLaunchResult.CALLED },
    ) : ReviewLauncher {
        var calls = 0
        var prepares = 0
        override fun canPresent() = presentable
        override suspend fun prepare() {
            prepares++
        }
        override suspend fun launch(): ReviewLaunchResult {
            calls++
            return body()
        }
    }

    /** Settings whose writes can be made to throw or to vanish, to break the store on purpose. */
    private class BreakableSettings(private val delegate: Settings) : Settings by delegate {
        enum class Mode { OK, THROW, DROP }

        var mode = Mode.OK

        private inline fun write(block: () -> Unit) = when (mode) {
            Mode.OK -> block()
            Mode.THROW -> throw IllegalStateException("disk full")
            Mode.DROP -> Unit
        }

        override fun putLong(key: String, value: Long) = write { delegate.putLong(key, value) }
        override fun putInt(key: String, value: Int) = write { delegate.putInt(key, value) }
        override fun putString(key: String, value: String) = write { delegate.putString(key, value) }
        override fun remove(key: String) = write { delegate.remove(key) }
    }

    private inner class Harness(val test: TestScope, val zone: TimeZone, private val startMs: Long) {
        val backing = MapSettings()
        val settings = BreakableSettings(backing)
        val store = ReviewStateStore(settings)
        val env = FakeEnvironment()
        val playback = MutableStateFlow(PlaybackState())
        val analytics = RecordingAnalytics()
        val clock = object : ReviewClock {
            override fun nowMs() = startMs + test.testScheduler.currentTime
            override fun timeZone() = zone
        }
        val health = SessionHealth { clock.nowMs() }
        val coordinator = newCoordinator()

        /** A second process over the same storage (fresh in-memory state). */
        fun newCoordinator() = ReviewCoordinator(
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
        fun seedEligible() = store.save(
            ReviewState(
                firstSeenMs = clock.nowMs() - 30 * DAY_MS,
                qualifyingDays = listOf("2026-09-28", "2026-09-30", today()),
            )
        )

        /** Long-time listener whose third qualifying day is today's to earn. */
        fun seedTwoDays() = store.save(
            ReviewState(firstSeenMs = clock.nowMs() - 30 * DAY_MS, qualifyingDays = listOf("2026-09-28", "2026-09-30"))
        )

        /** Starts in the foreground on [screen]; by default stays there past the dwell gate. */
        fun startOn(screen: ReviewScreen = ReviewScreen.EPISODES, dwell: Boolean = true) {
            coordinator.start()
            coordinator.onScreen(screen, resumed = true, windowFocused = true)
            test.runCurrent()
            if (dwell) advance(ReviewPolicy.MIN_FOREGROUND_MS)
        }

        fun advance(ms: Long) {
            test.advanceTimeBy(ms)
            test.runCurrent()
        }

        /** In-app navigation (foreground, focused). */
        fun go(screen: ReviewScreen, target: ReviewCoordinator = coordinator) {
            target.onScreen(screen, resumed = true, windowFocused = true)
            test.runCurrent()
        }

        fun emit(transform: PlaybackState.() -> PlaybackState) {
            playback.value = playback.value.transform()
            test.runCurrent()
        }

        fun play() = emit {
            copy(isPlaying = true, playWhenReady = true, hasEnded = false, currentDayDate = currentDayDate ?: activeDay)
        }

        /** A pause command (in-app button, lock screen, headset): intent goes too. */
        fun pause() = emit { copy(isPlaying = false, playWhenReady = false) }

        /** Audio held back by a call / focus loss / buffering: the listener still wants it. */
        fun holdBack() = emit { copy(isPlaying = false) }

        /** The queue ran out. Media3 keeps playWhenReady at STATE_ENDED. */
        fun finish() = emit { copy(isPlaying = false, hasEnded = true) }

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

        /** Open the player, listen, pause there, go back to the list: a LEFT_PLAYER moment. */
        fun listenInPlayer(ms: Long = 5_000L) {
            go(ReviewScreen.PLAYER)
            listen(ms)
            go(ReviewScreen.EPISODES)
        }

        fun settle() = advance(ReviewPolicy.SETTLE_MS)

        fun events(name: String) = analytics.events.filter { it.first == name }

        fun requests() = events(AnalyticsEvents.IN_APP_REVIEW_REQUESTED)
    }

    private fun reviewTest(
        zone: TimeZone = TimeZone.UTC,
        startMs: Long = baseMs,
        block: Harness.() -> Unit,
    ) = runTest { Harness(this, zone, startMs).block() }

    // ---- listening credit ----------------------------------------------------------------

    @Test
    fun tenMinutesOnThreeDaysBuildsThreeQualifyingDays() = reviewTest {
        startOn(ReviewScreen.OTHER)
        repeat(3) { day ->
            listen(10 * 60_000L)
            assertEquals(day + 1, store.load().qualifyingDays.size, "after day ${day + 1}")
            advance(DAY_MS)
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
    fun aLongGapBetweenPlayingEmissionsEarnsNothing() = reviewTest {
        startOn(ReviewScreen.OTHER)
        play()
        advance(60_000)
        emit { copy(currentPositionMs = 60_000) }
        pause()
        assertEquals(0L, store.load().listenDayMs)
    }

    @Test
    fun creditBeforeLocalMidnightStaysOnThatDay() = reviewTest {
        // 12:00 → 23:59:00 (start's dwell included), then listen two minutes across midnight.
        test.advanceTimeBy(11 * 3_600_000L + 59 * 60_000L - ReviewPolicy.MIN_FOREGROUND_MS)
        startOn(ReviewScreen.OTHER)
        listen(2 * 60_000L)
        val state = store.load()
        assertEquals("2026-10-02", state.listenDayKey)
        // Only the ~60 s after midnight (each interval counts toward the day it ends on).
        assertTrue(state.listenDayMs in 60_000L..61_000L, "listenDayMs=${state.listenDayMs}")
    }

    @Test
    fun inLosAngelesListeningAcrossUtcMidnightIsOneLocalDay() = reviewTest(losAngeles, laEveningMs) {
        seedTwoDays()
        val launcher = FakeLauncher()
        coordinator.attach(launcher)
        startOn()
        // 16:50:30 → 17:00:30 PDT, i.e. 23:50:30Z → 00:00:30Z. By UTC days neither date
        // would reach 10 minutes; by the listener's own day it is one 10-minute day.
        listenInPlayer(10 * 60_000L)
        assertEquals(listOf("2026-09-28", "2026-09-30", "2026-10-01"), store.load().qualifyingDays)
        settle()
        assertEquals(1, launcher.calls)
        assertEquals(3, requests().single().second[AnalyticsEvents.PARAM_QUALIFYING_DAYS])
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

    @Test
    fun futureStampsFromASkewedClockAreClampedAtStart() = reviewTest {
        val now = clock.nowMs()
        store.save(ReviewState(firstSeenMs = now + 400 * DAY_MS, lastCrashMs = now + DAY_MS))
        coordinator.start()
        test.runCurrent()
        val state = store.load()
        assertEquals(now, state.firstSeenMs)
        assertEquals(now, state.lastCrashMs)
    }

    // ---- the happy paths -------------------------------------------------------------------

    @Test
    fun leftPlayerAfterAPauseThereRequestsOnceAfterSettleAndPersistsFirst() = reviewTest {
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

        listenInPlayer()
        assertTrue(coordinator.hasPendingMoment)
        val leftAt = clock.nowMs()

        advance(ReviewPolicy.SETTLE_MS - 1)
        assertEquals(0, launcher.calls, "must wait out the settle delay")
        assertEquals(1, launcher.prepares, "the platform flow is warmed during the settle")

        advance(1)
        assertEquals(1, launcher.calls)

        // Persist-first: the attempt was already on disk when the platform was called.
        assertEquals(1, countSeenInsideLaunch)
        assertEquals(leftAt + ReviewPolicy.SETTLE_MS, lastRequestSeenInsideLaunch)

        val stored = store.load()
        assertEquals(1, stored.requestCount)
        assertEquals("2.1.0", stored.lastRequestVersion)
        assertEquals(emptyList(), stored.qualifyingDays)

        val (_, params) = requests().single()
        assertEquals("left_player", params[AnalyticsEvents.PARAM_MOMENT])
        assertEquals(1, params[AnalyticsEvents.PARAM_REQUEST_NUMBER])
        assertEquals(3, params[AnalyticsEvents.PARAM_QUALIFYING_DAYS])
        assertEquals("called", params[AnalyticsEvents.PARAM_RESULT])
    }

    @Test
    fun finishingTheDayOnTheListRequests() = reviewTest {
        seedEligible()
        val launcher = FakeLauncher()
        coordinator.attach(launcher)
        startOn()
        play()
        tickPlaying(5_000)
        finish()
        assertTrue(coordinator.hasPendingMoment)
        settle()
        assertEquals(1, launcher.calls)
        assertEquals("finished_on_list", requests().single().second[AnalyticsEvents.PARAM_MOMENT])
    }

    @Test
    fun finishingTheDayInThePlayerThenLeavingRequests() = reviewTest {
        seedEligible()
        val launcher = FakeLauncher()
        coordinator.attach(launcher)
        startOn()
        go(ReviewScreen.PLAYER)
        play()
        tickPlaying(5_000)
        finish()
        assertFalse(coordinator.hasPendingMoment, "not while the player is on screen")
        go(ReviewScreen.EPISODES)
        settle()
        assertEquals(1, launcher.calls)
        assertEquals("left_player", requests().single().second[AnalyticsEvents.PARAM_MOMENT])
    }

    /** End to end with the prompt on: install, then 10 minutes a day until every gate opens. */
    @Test
    fun aNewListenerIsFirstAskedOnTheDayInstallAgeIsReached() = reviewTest {
        val launcher = FakeLauncher()
        coordinator.attach(launcher)
        startOn()
        for (day in 0..5) {
            if (day > 0) advance(baseMs + day * DAY_MS - clock.nowMs())
            listenInPlayer(10 * 60_000L)
            settle()
            // Days 0–4: three+ listening days, today qualifies, but installed < 5 days ago.
            assertEquals(if (day < 5) 0 else 1, launcher.calls, "day $day")
        }
        val params = requests().single().second
        assertEquals(6, params[AnalyticsEvents.PARAM_QUALIFYING_DAYS])
        val skipped = events(AnalyticsEvents.IN_APP_REVIEW_SKIPPED).single().second
        assertEquals("too_new", skipped[AnalyticsEvents.PARAM_REASON])
    }

    @Test
    fun tenMinutesTodayEarnsTheThirdDayAndAsks() = reviewTest {
        seedTwoDays()
        val launcher = FakeLauncher()
        coordinator.attach(launcher)
        startOn()
        listenInPlayer(10 * 60_000L)
        settle()
        assertEquals(1, launcher.calls)
        assertEquals(3, requests().single().second[AnalyticsEvents.PARAM_QUALIFYING_DAYS])
    }

    /**
     * Only ALREADY_THIS_PROCESS can block the second moment here: the state is re-seeded as
     * fully eligible (last ask long ago, on an older version), and a new process over that
     * same state does ask. (The app version is read once per process, as it can't change.)
     */
    @Test
    fun aSecondMomentInTheSameProcessNeverLaunchesAgain() = reviewTest {
        seedEligible()
        val launcher = FakeLauncher()
        coordinator.attach(launcher)
        startOn()
        listenInPlayer()
        settle()
        assertEquals(1, launcher.calls)

        val reseeded = ReviewState(
            firstSeenMs = clock.nowMs() - 400 * DAY_MS,
            qualifyingDays = listOf("2026-09-28", "2026-09-30", today()),
            lastRequestMs = clock.nowMs() - 200 * DAY_MS,
            requestCount = 1,
            lastRequestVersion = "2.0.0",
        )
        store.save(reseeded)
        coordinator.stateForTest = reseeded

        listenInPlayer()
        settle()
        play()
        tickPlaying(1_000)
        finish()
        settle()
        assertEquals(1, launcher.calls)
        assertEquals(1, store.load().requestCount)

        // Control: the same state in a new process asks.
        val nextProcess = newCoordinator()
        val nextLauncher = FakeLauncher()
        nextProcess.attach(nextLauncher)
        nextProcess.start()
        go(ReviewScreen.EPISODES, nextProcess)
        advance(ReviewPolicy.MIN_FOREGROUND_MS)
        go(ReviewScreen.PLAYER, nextProcess)
        listen(5_000)
        go(ReviewScreen.EPISODES, nextProcess)
        settle()
        assertEquals(1, nextLauncher.calls)
        assertEquals(1, launcher.calls)
    }

    // ---- what is NOT a moment ----------------------------------------------------------------

    /** Apple: never request "in response to a button tap or other user action". */
    @Test
    fun aPauseTapOnTheListIsNotAMoment() = reviewTest {
        seedEligible()
        val launcher = FakeLauncher()
        coordinator.attach(launcher)
        startOn()
        listen(5_000)
        assertFalse(coordinator.hasPendingMoment)
        settle()
        assertEquals(0, launcher.calls)
    }

    @Test
    fun openingThePlayerAndBackingOutIsNotAMoment() = reviewTest {
        seedEligible()
        playback.value = PlaybackState(currentDayDate = activeDay) // loaded, paused
        val launcher = FakeLauncher()
        coordinator.attach(launcher)
        startOn()
        go(ReviewScreen.PLAYER)
        go(ReviewScreen.EPISODES)
        assertFalse(coordinator.hasPendingMoment)
    }

    @Test
    fun audioHeldBackByTheSystemIsNotAStop() = reviewTest {
        seedEligible()
        val launcher = FakeLauncher()
        coordinator.attach(launcher)
        startOn()
        go(ReviewScreen.PLAYER)
        play()
        tickPlaying(5_000)
        holdBack() // notification sound, Maps prompt, a call, Siri
        go(ReviewScreen.EPISODES)
        assertFalse(coordinator.hasPendingMoment)
    }

    @Test
    fun peekingAtAnotherDayIsNotAStop() = reviewTest {
        seedEligible()
        val launcher = FakeLauncher()
        coordinator.attach(launcher)
        startOn()
        go(ReviewScreen.PLAYER)
        play()
        tickPlaying(5_000)
        // loadDay(autoPlay = false) for another date replaces the queue.
        emit { copy(currentDayDate = "2026-09-30", isPlaying = false, playWhenReady = false) }
        go(ReviewScreen.EPISODES)
        assertFalse(coordinator.hasPendingMoment)
    }

    @Test
    fun aStopFromTheLockScreenThenReopeningOnThePlayerIsNotAMoment() = reviewTest {
        seedEligible()
        val launcher = FakeLauncher()
        coordinator.attach(launcher)
        startOn()
        go(ReviewScreen.PLAYER)
        play()
        tickPlaying(5_000)
        coordinator.onScreen(ReviewScreen.PLAYER, resumed = false, windowFocused = false) // screen off
        pause() // lock-screen / headphone control
        advance(10 * 60_000L)
        coordinator.onScreen(ReviewScreen.PLAYER, resumed = true, windowFocused = true) // reopened
        advance(60_000)
        go(ReviewScreen.EPISODES) // first back press
        assertFalse(coordinator.hasPendingMoment)
    }

    @Test
    fun aStopFromTheNotificationShadeIsNotAMoment() = reviewTest {
        seedEligible()
        val launcher = FakeLauncher()
        coordinator.attach(launcher)
        startOn()
        go(ReviewScreen.PLAYER)
        play()
        tickPlaying(5_000)
        coordinator.onScreen(ReviewScreen.PLAYER, resumed = true, windowFocused = false) // shade down
        pause()
        coordinator.onScreen(ReviewScreen.PLAYER, resumed = true, windowFocused = true)
        go(ReviewScreen.EPISODES)
        assertFalse(coordinator.hasPendingMoment)
    }

    @Test
    fun aStopBeforeOpeningThePlayerDoesNotCount() = reviewTest {
        seedEligible()
        val launcher = FakeLauncher()
        coordinator.attach(launcher)
        startOn()
        listen(5_000) // paused on the list
        go(ReviewScreen.PLAYER)
        go(ReviewScreen.EPISODES)
        assertFalse(coordinator.hasPendingMoment)
    }

    @Test
    fun playingAgainCancelsTheStop() = reviewTest {
        seedEligible()
        val launcher = FakeLauncher()
        coordinator.attach(launcher)
        startOn()
        go(ReviewScreen.PLAYER)
        listen(5_000)
        play()
        go(ReviewScreen.EPISODES)
        assertFalse(coordinator.hasPendingMoment)
    }

    @Test
    fun finishingOffTheListOrOutOfFocusIsNotAMoment() = reviewTest {
        seedEligible()
        val launcher = FakeLauncher()
        coordinator.attach(launcher)
        startOn(ReviewScreen.ABOUT)

        for ((screen, resumed, focused) in listOf(
            Triple(ReviewScreen.ABOUT, true, true),
            Triple(ReviewScreen.EPISODES, false, false), // screen off / Control Center / Auto
            Triple(ReviewScreen.EPISODES, true, false), // notification shade, dialog window
        )) {
            coordinator.onScreen(screen, resumed, focused)
            test.runCurrent()
            play()
            tickPlaying(1_000)
            finish()
            assertFalse(coordinator.hasPendingMoment, "$screen resumed=$resumed focused=$focused")
        }
        assertEquals(0, launcher.calls)
    }

    @Test
    fun foregroundingOnTheSameScreenIsNotAMoment() = reviewTest {
        seedEligible()
        playback.value = PlaybackState(currentDayDate = activeDay)
        val launcher = FakeLauncher()
        coordinator.attach(launcher)
        coordinator.start()
        coordinator.onScreen(ReviewScreen.EPISODES, resumed = false, windowFocused = false)
        coordinator.onScreen(ReviewScreen.EPISODES, resumed = true, windowFocused = true)
        assertFalse(coordinator.hasPendingMoment)
        settle()
        assertEquals(0, launcher.calls)
    }

    @Test
    fun aRecreatedHostOpeningOnEpisodesIsNotLeftPlayer() = reviewTest {
        seedEligible()
        val launcher = FakeLauncher()
        coordinator.attach(launcher)
        startOn()
        go(ReviewScreen.PLAYER)
        listen(5_000)
        // ReviewPromptHost.onDispose; the next host starts on Episodes.
        coordinator.onHostDisposed()
        coordinator.onScreen(ReviewScreen.OTHER, resumed = false, windowFocused = false)
        coordinator.onScreen(ReviewScreen.EPISODES, resumed = true, windowFocused = true)
        assertFalse(coordinator.hasPendingMoment)
        settle()
        assertEquals(0, launcher.calls)
    }

    /**
     * The Activity finished while playing (its ViewModel disconnects the controller); the
     * day ran out unseen; a new Activity reconnects. Its first update must be neither a
     * finish on the list nor listening time.
     */
    @Test
    fun aReconnectAfterTheHostWentAwayIsNeitherAFinishNorListening() = reviewTest {
        seedTwoDays()
        val launcher = FakeLauncher()
        coordinator.attach(launcher)
        startOn()
        go(ReviewScreen.PLAYER)
        play()
        tickPlaying(5_000)
        coordinator.onHostDisposed()
        assertEquals(5_000L, store.load().listenDayMs, "credit so far is flushed")

        advance(10 * 60_000L)
        coordinator.onScreen(ReviewScreen.OTHER, resumed = false, windowFocused = false)
        coordinator.onScreen(ReviewScreen.EPISODES, resumed = true, windowFocused = true)
        test.runCurrent()
        advance(ReviewPolicy.MIN_FOREGROUND_MS)
        finish() // first update from the reconnected controller
        assertFalse(coordinator.hasPendingMoment)
        go(ReviewScreen.PLAYER)
        go(ReviewScreen.EPISODES)
        assertFalse(coordinator.hasPendingMoment)
        settle()
        assertEquals(0, launcher.calls)
        assertEquals(5_000L, store.load().listenDayMs)
    }

    @Test
    fun aStaleGapEarnsNoCreditAndAPauseOnTheListIsNoMoment() = reviewTest {
        seedTwoDays()
        val launcher = FakeLauncher()
        coordinator.attach(launcher)
        startOn()
        play()
        coordinator.onScreen(ReviewScreen.OTHER, resumed = true, windowFocused = true)
        advance(10 * 60_000L)
        coordinator.onScreen(ReviewScreen.EPISODES, resumed = true, windowFocused = true)
        pause()
        assertFalse(coordinator.hasPendingMoment)
        assertEquals(0L, store.load().listenDayMs)
    }

    // ---- live re-checks during the settle -------------------------------------------------

    @Test
    fun resumingPlaybackDuringSettleCancelsTheAsk() = reviewTest {
        seedEligible()
        val launcher = FakeLauncher()
        coordinator.attach(launcher)
        startOn()
        listenInPlayer()
        advance(1_000)
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
        listenInPlayer()
        advance(1_000)
        coordinator.onScreen(ReviewScreen.EPISODES, resumed = false, windowFocused = false)
        settle()
        assertEquals(0, launcher.calls)
        assertEquals(0, store.load().requestCount)
    }

    @Test
    fun losingWindowFocusDuringSettleCancelsTheAsk() = reviewTest {
        seedEligible()
        val launcher = FakeLauncher()
        coordinator.attach(launcher)
        startOn()
        listenInPlayer()
        advance(1_000)
        coordinator.onScreen(ReviewScreen.EPISODES, resumed = true, windowFocused = false) // shade / dialog
        settle()
        assertEquals(0, launcher.calls)
    }

    @Test
    fun aHoldDuringSettleCancelsTheAskAndReleasingItAllowsTheNext() = reviewTest {
        seedEligible()
        val launcher = FakeLauncher()
        coordinator.attach(launcher)
        startOn()
        val dialog = Any()
        listenInPlayer()
        coordinator.setHold(dialog, true) // delete-episode dialog opened within the settle
        settle()
        assertEquals(0, launcher.calls)

        coordinator.setHold(dialog, false)
        listenInPlayer()
        settle()
        assertEquals(1, launcher.calls)
    }

    @Test
    fun rightAfterOpeningTheAppIsNeverAMoment() = reviewTest {
        seedEligible()
        val launcher = FakeLauncher()
        coordinator.attach(launcher)
        startOn(dwell = false)
        listenInPlayer() // 5 s of listening, 2 s settle: 7 s in the foreground
        settle()
        assertEquals(0, launcher.calls)
        assertTrue(events(AnalyticsEvents.IN_APP_REVIEW_SKIPPED).isEmpty(), "JUST_OPENED is not reported")

        advance(ReviewPolicy.MIN_FOREGROUND_MS)
        listenInPlayer()
        settle()
        assertEquals(1, launcher.calls)
    }

    @Test
    fun aFailureHoldsTheAskUntilASuccess() = reviewTest {
        seedEligible()
        val launcher = FakeLauncher()
        coordinator.attach(launcher)
        startOn()
        health.reportFailure("feed_refresh") // offline cold start: never shown to the listener
        listenInPlayer()
        settle()
        assertEquals(0, launcher.calls)
        assertEquals("session_failure", events(AnalyticsEvents.IN_APP_REVIEW_SKIPPED).single().second[AnalyticsEvents.PARAM_REASON])

        health.reportSuccess("download")
        listenInPlayer()
        settle()
        assertEquals(1, launcher.calls)
    }

    @Test
    fun aFailureExpiresAfterTheQuietPeriod() = reviewTest {
        seedEligible()
        val launcher = FakeLauncher()
        coordinator.attach(launcher)
        startOn()
        health.reportFailure("download")
        advance(ReviewPolicy.FAILURE_QUIET_MS)
        listenInPlayer()
        settle()
        assertEquals(1, launcher.calls)
    }

    @Test
    fun castingSuppressesTheAsk() = reviewTest {
        seedEligible()
        env.casting = true
        val launcher = FakeLauncher()
        coordinator.attach(launcher)
        startOn()
        listenInPlayer()
        settle()
        assertEquals(0, launcher.calls)
    }

    @Test
    fun noLauncherSpendsNoAttempt() = reviewTest {
        seedEligible()
        startOn()
        listenInPlayer()
        settle()
        assertEquals(0, store.load().requestCount)
        assertTrue(requests().isEmpty())
    }

    @Test
    fun aLauncherThatCannotPresentSpendsNothing() = reviewTest {
        seedEligible()
        val launcher = FakeLauncher(presentable = false)
        coordinator.attach(launcher)
        startOn()
        listenInPlayer()
        settle()
        assertEquals(0, launcher.calls)
        assertEquals(0, launcher.prepares)
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
        listenInPlayer()
        settle()
        assertEquals(0, old.calls)
        assertEquals(1, current.calls)
    }

    // ---- a broken store never lets the app ask --------------------------------------------

    @Test
    fun aStoreThatThrowsOnSaveNeverLaunches() = reviewTest {
        seedEligible()
        val launcher = FakeLauncher()
        coordinator.attach(launcher)
        startOn()
        settings.mode = BreakableSettings.Mode.THROW
        repeat(2) {
            listenInPlayer()
            settle()
        }
        assertEquals(0, launcher.calls)
        assertTrue(requests().isEmpty())
    }

    @Test
    fun aStoreThatDropsWritesNeverLaunches() = reviewTest {
        seedEligible()
        val launcher = FakeLauncher()
        coordinator.attach(launcher)
        startOn()
        settings.mode = BreakableSettings.Mode.DROP
        repeat(2) {
            listenInPlayer()
            settle()
        }
        assertEquals(0, launcher.calls)
        assertTrue(requests().isEmpty())
    }

    // ---- an attempt the platform never acted on is given back -------------------------------

    @Test
    fun contextGoneGivesTheAttemptBackButNeverAsksTwiceInAProcess() = reviewTest {
        seedEligible()
        val launcher = FakeLauncher { ReviewLaunchResult.CONTEXT_GONE }
        coordinator.attach(launcher)
        startOn()
        val before = store.load()
        listenInPlayer()
        settle()
        assertEquals(1, launcher.calls)
        assertEquals(before, store.load())
        assertEquals("context_gone", requests().single().second[AnalyticsEvents.PARAM_RESULT])

        listenInPlayer()
        settle()
        assertEquals(1, launcher.calls)
    }

    @Test
    fun anUnavailableFlowIsGivenBackWithARetryGap() = reviewTest {
        seedEligible()
        val launcher = FakeLauncher { throw IllegalStateException("no Play Store") }
        coordinator.attach(launcher)
        startOn()
        val before = store.load()
        listenInPlayer()
        val attemptAt = clock.nowMs() + ReviewPolicy.SETTLE_MS
        settle()
        assertEquals(1, launcher.calls)
        assertEquals(before.copy(lastUnavailableMs = attemptAt), store.load())
        assertEquals("unavailable", requests().single().second[AnalyticsEvents.PARAM_RESULT])

        listenInPlayer()
        settle()
        assertEquals(1, launcher.calls)
    }

    @Test
    fun aGiveBackThatCannotPersistLeavesTheAttemptSpent() = reviewTest {
        seedEligible()
        val launcher = FakeLauncher {
            settings.mode = BreakableSettings.Mode.THROW
            ReviewLaunchResult.CONTEXT_GONE
        }
        coordinator.attach(launcher)
        startOn()
        listenInPlayer()
        settle()
        assertEquals(1, launcher.calls)
        assertEquals(1, ReviewStateStore(backing).load().requestCount)
    }

    // ---- crash, kill switch, telemetry ------------------------------------------------------

    @Test
    fun aCrashOnThePreviousRunHoldsThePrompt() = reviewTest {
        seedEligible()
        env.crashed = true
        val launcher = FakeLauncher()
        coordinator.attach(launcher)
        coordinator.start()
        assertEquals(clock.nowMs(), store.load().lastCrashMs)
        startOn()
        listenInPlayer()
        settle()
        assertEquals(0, launcher.calls)
        assertEquals(0, store.load().requestCount)
    }

    @Test
    fun disabledAutoPromptIsADryRunThatStillCountsListeningDays() = reviewTest {
        env.autoPromptEnabled = false
        seedTwoDays()
        val launcher = FakeLauncher()
        coordinator.attach(launcher)
        startOn()
        listenInPlayer(10 * 60_000L)
        settle()
        assertEquals(0, launcher.calls)
        assertEquals(0, launcher.prepares)
        assertEquals(listOf("2026-09-28", "2026-09-30", "2026-10-01"), store.load().qualifyingDays)
        assertEquals(0, store.load().requestCount)
        val wouldRequest = events(AnalyticsEvents.IN_APP_REVIEW_WOULD_REQUEST).single().second
        assertEquals("left_player", wouldRequest[AnalyticsEvents.PARAM_MOMENT])
        assertEquals(3, wouldRequest[AnalyticsEvents.PARAM_QUALIFYING_DAYS])

        // Once per process; afterwards moments aren't even evaluated.
        listenInPlayer()
        assertFalse(coordinator.hasPendingMoment)
        assertEquals(1, events(AnalyticsEvents.IN_APP_REVIEW_WOULD_REQUEST).size)
        assertTrue(requests().isEmpty())
    }

    @Test
    fun dryRunSkipsAreReportedOncePerProcess() = reviewTest {
        env.autoPromptEnabled = false
        store.save(ReviewState(firstSeenMs = clock.nowMs() - DAY_MS))
        val launcher = FakeLauncher()
        coordinator.attach(launcher)
        startOn()
        repeat(2) {
            listenInPlayer()
            settle()
        }
        val skipped = events(AnalyticsEvents.IN_APP_REVIEW_SKIPPED).single().second
        assertEquals("too_new", skipped[AnalyticsEvents.PARAM_REASON])
        assertTrue(events(AnalyticsEvents.IN_APP_REVIEW_WOULD_REQUEST).isEmpty())
        assertEquals(0, launcher.calls)
    }

    @Test
    fun anEngagementSkipIsReportedOncePerProcess() = reviewTest {
        seedTwoDays()
        val launcher = FakeLauncher()
        coordinator.attach(launcher)
        startOn()
        repeat(2) {
            listenInPlayer() // 5 s: today doesn't qualify
            settle()
        }
        val skipped = events(AnalyticsEvents.IN_APP_REVIEW_SKIPPED).single().second
        assertEquals("today_not_qualified", skipped[AnalyticsEvents.PARAM_REASON])
        assertEquals(2, skipped[AnalyticsEvents.PARAM_QUALIFYING_DAYS])
        assertEquals("left_player", skipped[AnalyticsEvents.PARAM_MOMENT])
        assertEquals(0, launcher.calls)
        assertEquals(0, launcher.prepares, "no warm-up when a pure gate already fails")
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
