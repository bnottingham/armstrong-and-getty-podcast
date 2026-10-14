package com.nomnomsom.armstrongandgetty.review

import com.nomnomsom.armstrongandgetty.review.ReviewPolicy.COOLDOWN_MS
import com.nomnomsom.armstrongandgetty.review.ReviewPolicy.CRASH_QUIET_MS
import com.nomnomsom.armstrongandgetty.review.ReviewPolicy.DAY_MS
import com.nomnomsom.armstrongandgetty.review.ReviewPolicy.MIN_INSTALL_AGE_MS
import com.nomnomsom.armstrongandgetty.util.formatAsDayKey
import kotlinx.datetime.FixedOffsetTimeZone
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.time.Instant

/**
 * Pure gate tests for the in-app rating prompt. Every SkipReason test changes exactly one
 * input from [eligible], which also locks in the evaluation order.
 */
class ReviewPolicyTest {

    private val now: Long = utcMs(2026, 10, 13, 18, 0)
    private val today = "2026-10-13"
    private val version = "2.1.0"

    private fun eligibleState() = ReviewState(
        firstSeenMs = now - 30 * DAY_MS,
        qualifyingDays = listOf("2026-10-09", "2026-10-11", today),
    )

    private fun eligibleContext() = ReviewContext(
        nowMs = now,
        todayKey = today,
        appVersion = version,
        autoPromptEnabled = true,
        screen = ReviewScreen.EPISODES,
        isResumed = true,
        isPlaying = false,
        hasActiveDay = true,
        isCasting = false,
        sessionHadFailure = false,
        requestedThisProcess = false,
        hasLauncher = true,
    )

    private fun eval(
        state: ReviewState = eligibleState(),
        ctx: ReviewContext = eligibleContext(),
    ) = ReviewPolicy.evaluate(state, ctx)

    private fun skip(reason: SkipReason) = ReviewDecision.Skip(reason)

    // ---- baseline ----------------------------------------------------------------------

    @Test
    fun eligibleBaselineRequests() {
        assertEquals(ReviewDecision.Request, eval())
    }

    // ---- one test per SkipReason, in gate order ----------------------------------------

    @Test
    fun disabled() = assertEquals(skip(SkipReason.DISABLED), eval(ctx = eligibleContext().copy(autoPromptEnabled = false)))

    @Test
    fun alreadyThisProcess() =
        assertEquals(skip(SkipReason.ALREADY_THIS_PROCESS), eval(ctx = eligibleContext().copy(requestedThisProcess = true)))

    @Test
    fun noLauncher() = assertEquals(skip(SkipReason.NO_LAUNCHER), eval(ctx = eligibleContext().copy(hasLauncher = false)))

    @Test
    fun notForeground() = assertEquals(skip(SkipReason.NOT_FOREGROUND), eval(ctx = eligibleContext().copy(isResumed = false)))

    @Test
    fun notHomeScreen() {
        for (screen in listOf(ReviewScreen.PLAYER, ReviewScreen.ABOUT, ReviewScreen.OTHER)) {
            assertEquals(skip(SkipReason.NOT_HOME_SCREEN), eval(ctx = eligibleContext().copy(screen = screen)), "$screen")
        }
    }

    @Test
    fun playing() = assertEquals(skip(SkipReason.PLAYING), eval(ctx = eligibleContext().copy(isPlaying = true)))

    @Test
    fun noActiveDay() = assertEquals(skip(SkipReason.NO_ACTIVE_DAY), eval(ctx = eligibleContext().copy(hasActiveDay = false)))

    @Test
    fun casting() = assertEquals(skip(SkipReason.CASTING), eval(ctx = eligibleContext().copy(isCasting = true)))

    @Test
    fun sessionFailure() =
        assertEquals(skip(SkipReason.SESSION_FAILURE), eval(ctx = eligibleContext().copy(sessionHadFailure = true)))

    @Test
    fun tooNew() =
        assertEquals(skip(SkipReason.TOO_NEW), eval(state = eligibleState().copy(firstSeenMs = now - DAY_MS)))

    @Test
    fun clockSkew() =
        assertEquals(skip(SkipReason.CLOCK_SKEW), eval(state = eligibleState().copy(firstSeenMs = now + 1)))

    @Test
    fun recentCrash() =
        assertEquals(skip(SkipReason.RECENT_CRASH), eval(state = eligibleState().copy(lastCrashMs = now - DAY_MS)))

    @Test
    fun lifetimeCap() =
        assertEquals(skip(SkipReason.LIFETIME_CAP), eval(state = eligibleState().copy(requestCount = 3)))

    @Test
    fun versionAlreadyAsked() =
        assertEquals(skip(SkipReason.VERSION_ALREADY_ASKED), eval(state = eligibleState().copy(lastRequestVersion = version)))

    @Test
    fun cooldown() =
        assertEquals(skip(SkipReason.COOLDOWN), eval(state = eligibleState().copy(lastRequestMs = now - 10 * DAY_MS)))

    @Test
    fun todayNotQualified() = assertEquals(
        skip(SkipReason.TODAY_NOT_QUALIFIED),
        eval(state = eligibleState().copy(qualifyingDays = listOf("2026-10-09", "2026-10-10", "2026-10-11")))
    )

    @Test
    fun needsListeningDays() = assertEquals(
        skip(SkipReason.NEEDS_LISTENING_DAYS),
        eval(state = eligibleState().copy(qualifyingDays = listOf("2026-10-11", today)))
    )

    @Test
    fun everySkipReasonHasATest() {
        // 17 reasons, each covered above; fails if a reason is added without a gate test.
        assertEquals(17, SkipReason.entries.size)
    }

    // ---- clock edges (all inclusive) -----------------------------------------------------

    @Test
    fun installAgeBoundaryIsInclusive() {
        assertEquals(ReviewDecision.Request, eval(state = eligibleState().copy(firstSeenMs = now - MIN_INSTALL_AGE_MS)))
        assertEquals(
            skip(SkipReason.TOO_NEW),
            eval(state = eligibleState().copy(firstSeenMs = now - MIN_INSTALL_AGE_MS + 1))
        )
    }

    @Test
    fun cooldownBoundaryIsInclusive() {
        val asked = eligibleState().copy(requestCount = 1, lastRequestVersion = "2.0.0")
        assertEquals(ReviewDecision.Request, eval(state = asked.copy(lastRequestMs = now - COOLDOWN_MS)))
        assertEquals(skip(SkipReason.COOLDOWN), eval(state = asked.copy(lastRequestMs = now - COOLDOWN_MS + 1)))
    }

    @Test
    fun crashQuietBoundaryIsInclusive() {
        assertEquals(ReviewDecision.Request, eval(state = eligibleState().copy(lastCrashMs = now - CRASH_QUIET_MS)))
        assertEquals(
            skip(SkipReason.RECENT_CRASH),
            eval(state = eligibleState().copy(lastCrashMs = now - CRASH_QUIET_MS + 1))
        )
    }

    @Test
    fun clockMovedBackwardsSkipsConservatively() {
        assertEquals(skip(SkipReason.CLOCK_SKEW), eval(state = eligibleState().copy(firstSeenMs = now + 1)))
        assertEquals(
            skip(SkipReason.CLOCK_SKEW),
            eval(state = eligibleState().copy(lastRequestMs = now + 1, requestCount = 1, lastRequestVersion = "2.0.0"))
        )
        assertEquals(skip(SkipReason.CLOCK_SKEW), eval(state = eligibleState().copy(lastCrashMs = now + 1)))
    }

    @Test
    fun neverSeenIsTooNew() {
        assertEquals(skip(SkipReason.TOO_NEW), eval(state = eligibleState().copy(firstSeenMs = null)))
    }

    // ---- caps ----------------------------------------------------------------------------

    @Test
    fun lifetimeCapHoldsEvenAfterCooldownOnANewVersion() {
        val state = eligibleState().copy(
            requestCount = 3,
            lastRequestMs = now - 400 * DAY_MS,
            lastRequestVersion = "1.0.0",
        )
        assertEquals(skip(SkipReason.LIFETIME_CAP), eval(state = state))
    }

    @Test
    fun sameVersionNeverAsksTwiceEvenAfterLongGap() {
        val state = eligibleState().copy(requestCount = 1, lastRequestMs = now - 200 * DAY_MS, lastRequestVersion = version)
        assertEquals(skip(SkipReason.VERSION_ALREADY_ASKED), eval(state = state))
    }

    @Test
    fun newVersionStillWaitsForCooldown() {
        val state = eligibleState().copy(requestCount = 1, lastRequestMs = now - 119 * DAY_MS, lastRequestVersion = "2.0.0")
        assertEquals(skip(SkipReason.COOLDOWN), eval(state = state))
    }

    // ---- recordListening -----------------------------------------------------------------

    @Test
    fun dayQualifiesAtExactlyTenMinutes() {
        val almost = ReviewPolicy.recordListening(ReviewState(), 599_999, today)
        assertEquals(emptyList(), almost.qualifyingDays)
        assertEquals(599_999, almost.listenDayMs)
        val done = ReviewPolicy.recordListening(almost, 1, today)
        assertEquals(listOf(today), done.qualifyingDays)
    }

    @Test
    fun creditOnDifferentDaysDoesNotAddUp() {
        val day1 = ReviewPolicy.recordListening(ReviewState(), 400_000, "2026-10-12")
        val day2 = ReviewPolicy.recordListening(day1, 400_000, "2026-10-13")
        assertEquals(emptyList(), day2.qualifyingDays)
        assertEquals("2026-10-13", day2.listenDayKey)
        assertEquals(400_000, day2.listenDayMs)
    }

    @Test
    fun accumulatorResetsOnDayChange() {
        val day1 = ReviewPolicy.recordListening(ReviewState(), 700_000, "2026-10-12")
        val day2 = ReviewPolicy.recordListening(day1, 1_000, "2026-10-13")
        assertEquals(1_000, day2.listenDayMs)
        assertEquals(listOf("2026-10-12"), day2.qualifyingDays)
    }

    @Test
    fun sameDayIsNeverAddedTwice() {
        var state = ReviewPolicy.recordListening(ReviewState(), 600_000, today)
        state = ReviewPolicy.recordListening(state, 600_000, today)
        state = ReviewPolicy.recordListening(state, 1, today)
        assertEquals(listOf(today), state.qualifyingDays)
    }

    @Test
    fun seventeenthDayDropsTheOldestAndStaysSorted() {
        var state = ReviewState()
        // Add out of order to prove sorting: days 17 down to 1 of October.
        for (d in 17 downTo 2) state = ReviewPolicy.recordListening(state, 600_000, dayKey(d))
        assertEquals(16, state.qualifyingDays.size)
        state = ReviewPolicy.recordListening(state, 600_000, dayKey(18))
        assertEquals(16, state.qualifyingDays.size)
        assertEquals((3..18).map(::dayKey), state.qualifyingDays)
    }

    @Test
    fun nonPositiveCreditIsANoOp() {
        val state = ReviewState(listenDayKey = today, listenDayMs = 5)
        assertSame(state, ReviewPolicy.recordListening(state, 0, today))
        assertSame(state, ReviewPolicy.recordListening(state, -5, "2026-10-14"))
    }

    // ---- recordRequest / recordFirstSeen -------------------------------------------------

    @Test
    fun recordRequestSpendsAnAttemptAndResetsEngagement() {
        val before = eligibleState().copy(listenDayKey = today, listenDayMs = 900_000)
        val after = ReviewPolicy.recordRequest(before, now, version)
        assertEquals(1, after.requestCount)
        assertEquals(now, after.lastRequestMs)
        assertEquals(version, after.lastRequestVersion)
        assertEquals(emptyList(), after.qualifyingDays)
        assertEquals(null, after.listenDayKey)
        assertEquals(0, after.listenDayMs)
        assertEquals(before.firstSeenMs, after.firstSeenMs)

        // Past the cooldown, on a new version, the next attempt must earn engagement again.
        val later = eligibleContext().copy(nowMs = now + COOLDOWN_MS, todayKey = "2027-02-10", appVersion = "2.2.0")
        assertEquals(skip(SkipReason.TODAY_NOT_QUALIFIED), ReviewPolicy.evaluate(after, later))
    }

    @Test
    fun recordFirstSeenIsIdempotent() {
        val first = ReviewPolicy.recordFirstSeen(ReviewState(), 1_000)
        assertEquals(1_000, first.firstSeenMs)
        assertSame(first, ReviewPolicy.recordFirstSeen(first, 9_999))
    }

    @Test
    fun recordCrashStampsTime() {
        assertEquals(42L, ReviewPolicy.recordCrash(ReviewState(), 42).lastCrashMs)
    }

    // ---- local vs UTC day boundaries -------------------------------------------------------

    private val pacificDaylight = FixedOffsetTimeZone(UtcOffset(hours = -7))

    @Test
    fun lateEveningPacificIsStillTheLocalDay() {
        val t = localMs(2026, 10, 1, 23, 30, pacificDaylight) // 2026-10-02T06:30Z
        assertEquals("2026-10-01", ReviewPolicy.localDayKey(t, pacificDaylight))
    }

    @Test
    fun playsStraddlingUtcMidnightCountAsOneLocalDay() {
        val t1 = localMs(2026, 10, 1, 16, 30, pacificDaylight) // 23:30Z on 10-01
        val t2 = localMs(2026, 10, 1, 17, 30, pacificDaylight) // 00:30Z on 10-02
        assertNotEquals(formatAsDayKey(Instant.fromEpochMilliseconds(t1)), formatAsDayKey(Instant.fromEpochMilliseconds(t2)))

        var state = ReviewPolicy.recordListening(ReviewState(), 6 * 60_000L, ReviewPolicy.localDayKey(t1, pacificDaylight))
        state = ReviewPolicy.recordListening(state, 6 * 60_000L, ReviewPolicy.localDayKey(t2, pacificDaylight))
        assertEquals(listOf("2026-10-01"), state.qualifyingDays)
    }

    @Test
    fun playsAcrossLocalMidnightAreTwoDaysEvenOnOneUtcDate() {
        val t1 = localMs(2026, 10, 1, 23, 50, pacificDaylight) // 06:50Z on 10-02
        val t2 = localMs(2026, 10, 2, 0, 10, pacificDaylight) // 07:10Z on 10-02
        assertEquals(formatAsDayKey(Instant.fromEpochMilliseconds(t1)), formatAsDayKey(Instant.fromEpochMilliseconds(t2)))

        var state = ReviewPolicy.recordListening(ReviewState(), 8 * 60_000L, ReviewPolicy.localDayKey(t1, pacificDaylight))
        state = ReviewPolicy.recordListening(state, 8 * 60_000L, ReviewPolicy.localDayKey(t2, pacificDaylight))
        assertEquals(emptyList(), state.qualifyingDays)
        assertEquals("2026-10-02", state.listenDayKey)
    }

    @Test
    fun farEastOfUtcIsAlreadyTomorrow() {
        val zone = FixedOffsetTimeZone(UtcOffset(hours = 13))
        assertEquals("2026-10-02", ReviewPolicy.localDayKey(utcMs(2026, 10, 1, 11, 30), zone))
    }

    @Test
    fun dstFallBackHourStaysOnTheSameLocalDay() {
        val la = TimeZone.of("America/Los_Angeles")
        val pdt = utcMs(2026, 11, 1, 8, 30) // 01:30 PDT
        val pst = utcMs(2026, 11, 1, 9, 30) // 01:30 PST (repeated hour)
        assertEquals("2026-11-01", ReviewPolicy.localDayKey(pdt, la))
        assertEquals("2026-11-01", ReviewPolicy.localDayKey(pst, la))
    }

    /**
     * Documents that the review gate's day key is NOT the episode engine's UTC day key
     * (PodcastRepository.todayKey / formatAsDayKey). Don't "unify" them.
     */
    @Test
    fun localDayKeyIsDeliberatelyNotTheEngineUtcKey() {
        val t = localMs(2026, 10, 1, 22, 0, pacificDaylight)
        assertNotEquals(formatAsDayKey(Instant.fromEpochMilliseconds(t)), ReviewPolicy.localDayKey(t, pacificDaylight))
    }

    // ---- helpers -------------------------------------------------------------------------

    private fun dayKey(day: Int) = "2026-10-${day.toString().padStart(2, '0')}"

    private fun utcMs(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long =
        LocalDateTime(y, mo, d, h, mi).toInstant(TimeZone.UTC).toEpochMilliseconds()

    private fun localMs(y: Int, mo: Int, d: Int, h: Int, mi: Int, zone: TimeZone): Long =
        LocalDateTime(y, mo, d, h, mi).toInstant(zone).toEpochMilliseconds()
}
