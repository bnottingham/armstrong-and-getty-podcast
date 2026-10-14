package com.nomnomsom.armstrongandgetty.review

import com.nomnomsom.armstrongandgetty.analytics.AnalyticsEvents
import com.nomnomsom.armstrongandgetty.analytics.AnalyticsTracker
import com.nomnomsom.armstrongandgetty.media.PlaybackState
import com.nomnomsom.armstrongandgetty.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Drives the in-app rating prompt: credits listening time from [playbackState], detects the
 * two natural moments ([ReviewMoment]), waits [ReviewPolicy.SETTLE_MS], re-checks every gate
 * with live values and — only on [ReviewDecision.Request] — persists the attempt, reads it
 * back, and then makes the one platform call through the attached [ReviewLauncher].
 *
 * Resolved lazily from Koin inside App() composition only (never from a service, worker,
 * BG task or Android Auto). All work runs on [scope]'s dispatcher (Main in production); this
 * class never switches dispatchers itself so tests can inject a TestScope.
 */
class ReviewCoordinator(
    private val store: ReviewStateStore,
    private val environment: ReviewEnvironment,
    private val playbackState: StateFlow<PlaybackState>,
    private val sessionHealth: SessionHealth,
    private val analytics: AnalyticsTracker,
    private val clock: ReviewClock = ReviewClock.System,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    private companion object {
        const val TAG = "InAppReview"
    }

    private var started = false
    private var state = ReviewState()

    private var launcher: ReviewLauncher? = null
    private var screen = ReviewScreen.OTHER
    private var resumed = false
    private var requestedThisProcess = false
    private var pending: Job? = null

    // Listening-credit tracker
    private var lastEmissionMs: Long? = null
    private var lastPlaying = false
    private var pendingCreditMs = 0L
    private var pendingDayKey: String? = null

    /** Idempotent. Records first-seen and a previous-run crash, then starts the one collector. */
    fun start() {
        if (started) return
        started = true
        val now = clock.nowMs()
        var next = ReviewPolicy.recordFirstSeen(loadOrDefault(), now)
        val crashed = try {
            environment.crashedOnPreviousRun()
        } catch (e: Exception) {
            AppLog.w(TAG, "crash check failed", e)
            false
        }
        if (crashed) next = ReviewPolicy.recordCrash(next, now)
        state = next
        persist(next)
        scope.launch { playbackState.collect(::onPlayback) }
    }

    /** Called by [ReviewPromptHost] on route and lifecycle changes. */
    fun onScreen(screen: ReviewScreen, resumed: Boolean) {
        val previous = this.screen
        this.screen = screen
        this.resumed = resumed
        if (previous == ReviewScreen.PLAYER && screen == ReviewScreen.EPISODES) {
            onMoment(ReviewMoment.LEFT_PLAYER)
        }
    }

    /** Test hook: a natural moment is waiting out its settle delay. */
    internal val hasPendingMoment: Boolean get() = pending?.isActive == true

    fun attach(launcher: ReviewLauncher) {
        this.launcher = launcher
    }

    /** Clears only if [launcher] is still the attached one, so an Activity recreate can't race. */
    fun detach(launcher: ReviewLauncher) {
        if (this.launcher === launcher) this.launcher = null
    }

    private fun onPlayback(playback: PlaybackState) {
        val now = clock.nowMs()
        val previousMs = lastEmissionMs
        val wasPlaying = lastPlaying
        lastEmissionMs = now
        lastPlaying = playback.isPlaying

        if (wasPlaying && previousMs != null) {
            val credit = (now - previousMs).coerceIn(0L, ReviewPolicy.MAX_TICK_CREDIT_MS)
            if (credit > 0L) addCredit(credit, now)
        }

        val pausedEdge = wasPlaying && !playback.isPlaying
        if (pausedEdge) flushCredit()
        if (pausedEdge && screen == ReviewScreen.EPISODES && resumed) {
            onMoment(ReviewMoment.PAUSED_ON_LIST)
        }
    }

    private fun addCredit(credit: Long, now: Long) {
        val dayKey = ReviewPolicy.localDayKey(now, clock.timeZone())
        // Never let credit earned before local midnight count toward the next day.
        if (pendingDayKey != null && pendingDayKey != dayKey) flushCredit()
        pendingDayKey = dayKey
        pendingCreditMs += credit

        if (dayKey in state.qualifyingDays) {
            // Today already counts: nothing more to learn, so no more writes today.
            pendingCreditMs = 0L
            return
        }
        val dayTotal = pendingCreditMs + if (state.listenDayKey == dayKey) state.listenDayMs else 0L
        if (pendingCreditMs >= ReviewPolicy.FLUSH_INTERVAL_MS ||
            dayTotal >= ReviewPolicy.MIN_LISTEN_MS_PER_DAY
        ) {
            flushCredit()
        }
    }

    private fun flushCredit() {
        val dayKey = pendingDayKey
        val credit = pendingCreditMs
        pendingCreditMs = 0L
        pendingDayKey = null
        if (dayKey == null || credit <= 0L || dayKey in state.qualifyingDays) return
        val next = ReviewPolicy.recordListening(state, credit, dayKey)
        if (next != state) {
            state = next
            persist(next)
        }
    }

    private fun onMoment(moment: ReviewMoment) {
        if (pending?.isActive == true) return
        pending = scope.launch {
            delay(ReviewPolicy.SETTLE_MS)
            attempt(moment)
        }
    }

    private suspend fun attempt(moment: ReviewMoment) {
        flushCredit()
        val now = clock.nowMs()
        val playback = playbackState.value
        val activeLauncher = launcher
        val ctx = ReviewContext(
            nowMs = now,
            todayKey = ReviewPolicy.localDayKey(now, clock.timeZone()),
            appVersion = guarded("appVersion", "unknown") { environment.appVersion() },
            autoPromptEnabled = environment.autoPromptEnabled,
            screen = screen,
            isResumed = resumed,
            isPlaying = playback.isPlaying,
            hasActiveDay = playback.currentDayDate != null,
            isCasting = guarded("isCasting", false) { environment.isCasting() },
            sessionHadFailure = sessionHealth.hadFailure.value,
            requestedThisProcess = requestedThisProcess,
            hasLauncher = activeLauncher != null,
        )

        val decision = ReviewPolicy.evaluate(state, ctx)
        if (decision is ReviewDecision.Skip || activeLauncher == null) {
            val reason = (decision as? ReviewDecision.Skip)?.reason ?: SkipReason.NO_LAUNCHER
            AppLog.d(TAG, "skip ${moment.analyticsValue}: $reason")
            return
        }

        // Persist first, read back, and only then ask: a broken store must never let the
        // app ask again and again.
        val qualifying = state.qualifyingDays.size
        val next = ReviewPolicy.recordRequest(state, now, ctx.appVersion)
        val persisted = try {
            store.save(next)
            store.load() == next
        } catch (e: Exception) {
            AppLog.w(TAG, "could not persist the attempt", e)
            false
        }
        if (!persisted) {
            AppLog.w(TAG, "skip ${moment.analyticsValue}: attempt did not persist")
            return
        }
        state = next
        requestedThisProcess = true

        val result = try {
            activeLauncher.launch()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w(TAG, "review launcher failed", e)
            ReviewLaunchResult.UNAVAILABLE
        }

        analytics.logEvent(
            AnalyticsEvents.IN_APP_REVIEW_REQUESTED,
            mapOf(
                AnalyticsEvents.PARAM_MOMENT to moment.analyticsValue,
                AnalyticsEvents.PARAM_REQUEST_NUMBER to next.requestCount,
                AnalyticsEvents.PARAM_QUALIFYING_DAYS to qualifying,
                AnalyticsEvents.PARAM_RESULT to result.analyticsValue,
            )
        )
        AppLog.i(
            TAG,
            "requested ${moment.analyticsValue}: #${next.requestCount}, " +
                "$qualifying qualifying days, result=${result.analyticsValue}"
        )
    }

    private fun loadOrDefault(): ReviewState = try {
        store.load()
    } catch (e: Exception) {
        AppLog.w(TAG, "could not load review state", e)
        ReviewState()
    }

    private fun persist(next: ReviewState) {
        try {
            store.save(next)
        } catch (e: Exception) {
            AppLog.w(TAG, "could not save review state", e)
        }
    }

    private inline fun <T> guarded(what: String, fallback: T, block: () -> T): T = try {
        block()
    } catch (e: Exception) {
        AppLog.w(TAG, "$what failed", e)
        fallback
    }
}
