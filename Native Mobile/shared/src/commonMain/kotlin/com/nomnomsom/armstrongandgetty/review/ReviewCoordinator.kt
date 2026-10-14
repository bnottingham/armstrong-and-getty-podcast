package com.nomnomsom.armstrongandgetty.review

import com.nomnomsom.armstrongandgetty.analytics.AnalyticsEvents
import com.nomnomsom.armstrongandgetty.analytics.AnalyticsTracker
import com.nomnomsom.armstrongandgetty.media.PlaybackState
import com.nomnomsom.armstrongandgetty.util.AppLog
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "InAppReview"

/** Production scope: Main, and a failure anywhere in here is logged, never a crash. */
private fun defaultReviewScope() = CoroutineScope(
    SupervisorJob() + Dispatchers.Main +
        CoroutineExceptionHandler { _, e -> AppLog.w(TAG, "review coordinator failed", e) }
)

/**
 * Drives the in-app rating prompt: credits listening time from [playbackState], detects the
 * two moments ([ReviewMoment]), waits [ReviewPolicy.SETTLE_MS], re-checks every gate with
 * live values and — only on [ReviewDecision.Request] — persists the attempt, reads it back,
 * and then makes the one platform call through the attached [ReviewLauncher].
 *
 * Resolved lazily from Koin inside App() composition only (never from a service, worker,
 * BG task or Android Auto). All work runs on [scope]'s dispatcher (Main in production); this
 * class never switches dispatchers itself so tests can inject a TestScope.
 *
 * With [ReviewEnvironment.autoPromptEnabled] off (Android today) every moment is a dry run:
 * the full policy is evaluated as if it were on, `in_app_review_would_request` is logged once
 * per process when it would ask, and nothing is spent or shown.
 */
class ReviewCoordinator(
    private val store: ReviewStateStore,
    private val environment: ReviewEnvironment,
    private val playbackState: StateFlow<PlaybackState>,
    private val sessionHealth: SessionHealth,
    private val analytics: AnalyticsTracker,
    private val clock: ReviewClock = ReviewClock.System,
    private val scope: CoroutineScope = defaultReviewScope(),
) {
    private var started = false
    private var state = ReviewState()
    private var cachedAppVersion: String? = null

    private var launcher: ReviewLauncher? = null
    private var screen = ReviewScreen.OTHER
    private var resumed = false
    private var windowFocused = false
    /** When the host last became resumed AND focused; null while it isn't. */
    private var interactiveSinceMs: Long? = null
    private val holds = mutableSetOf<Any>()
    private var requestedThisProcess = false
    private var pending: Job? = null

    // Once-per-process telemetry.
    private var loggedSkip = false
    private var loggedWouldRequest = false

    // Playback edge detector. lastEmissionMs == null means "no baseline yet": no edges, no credit.
    private var lastEmissionMs: Long? = null
    private var lastPlaying = false
    private var lastIntent = false
    private var lastEnded = false
    private var lastDay: String? = null

    /**
     * The listener stopped the day they were on (pause command or natural end) while the app
     * was interactive, and hasn't restarted it since. Cleared on every new foreground visit
     * and every new player visit, so a stop from the lock screen / notification / Auto, or
     * one from before opening the player, never makes leaving the player a moment.
     */
    private var listenEnded = false

    // Listening credit not yet persisted.
    private var pendingCreditMs = 0L
    private var pendingDayKey: String? = null

    private val isInteractive: Boolean get() = resumed && windowFocused

    /** Idempotent. Records first-seen and a previous-run crash, then starts the one collector. */
    fun start() {
        if (started) return
        started = true
        guarded("start", Unit) {
            val now = clock.nowMs()
            var next = ReviewPolicy.recordFirstSeen(loadOrDefault(), now)
            next = ReviewPolicy.clampFutureTimestamps(next, now)
            val crashed = guarded("crash check", false) { environment.crashedOnPreviousRun() }
            if (crashed) next = ReviewPolicy.recordCrash(next, now)
            state = next
            persist(next)
        }
        scope.launch { playbackState.collect(::onPlayback) }
    }

    /** Called by [ReviewPromptHost] on route, lifecycle and window-focus changes. */
    fun onScreen(screen: ReviewScreen, resumed: Boolean, windowFocused: Boolean) {
        val now = clock.nowMs()
        val previous = this.screen
        val wasInteractive = isInteractive
        this.screen = screen
        this.resumed = resumed
        this.windowFocused = windowFocused

        if (isInteractive && !wasInteractive) {
            interactiveSinceMs = now
            listenEnded = false
        } else if (!isInteractive) {
            interactiveSinceMs = null
        }
        if (screen == ReviewScreen.PLAYER && previous != ReviewScreen.PLAYER) listenEnded = false

        if (previous == ReviewScreen.PLAYER && screen == ReviewScreen.EPISODES && listenEnded) {
            onMoment(ReviewMoment.LEFT_PLAYER)
        }
    }

    /**
     * The host composition went away (Activity finished or recreated). Its PlaybackController
     * may disconnect and later reconnect, so forget the playback baseline: the first update
     * after that must never read as a stop, a natural end, or listening time.
     */
    fun onHostDisposed() {
        onScreen(ReviewScreen.OTHER, resumed = false, windowFocused = false)
        flushCredit()
        lastEmissionMs = null
        lastPlaying = false
        lastIntent = false
        lastEnded = false
        lastDay = null
    }

    /** [owner] holds the prompt while [held] (a dialog, a visible failure). Idempotent. */
    fun setHold(owner: Any, held: Boolean) {
        if (held) holds += owner else holds -= owner
    }

    /** Test hook: a moment is waiting out its settle delay. */
    internal val hasPendingMoment: Boolean get() = pending?.isActive == true

    /** Test hook: the in-memory state (the coordinator reads the store only at [start]). */
    internal var stateForTest: ReviewState
        get() = state
        set(value) {
            state = value
        }

    fun attach(launcher: ReviewLauncher) {
        this.launcher = launcher
    }

    /** Clears only if [launcher] is still the attached one, so an Activity recreate can't race. */
    fun detach(launcher: ReviewLauncher) {
        if (this.launcher === launcher) this.launcher = null
    }

    private fun onPlayback(playback: PlaybackState) = guarded("playback update", Unit) {
        val now = clock.nowMs()
        val previousMs = lastEmissionMs
        val wasPlaying = lastPlaying
        val wasIntent = lastIntent
        val wasEnded = lastEnded
        val previousDay = lastDay
        val intent = playback.intendsToPlay
        lastEmissionMs = now
        lastPlaying = playback.isPlaying
        lastIntent = intent
        lastEnded = playback.hasEnded
        lastDay = playback.currentDayDate

        // Credit only a recent interval: a long gap means the controller was disconnected
        // (or stalled) and nothing is known about what played in between.
        if (wasPlaying && previousMs != null) {
            val gap = now - previousMs
            if (gap in 1..ReviewPolicy.MAX_TICK_CREDIT_MS) addCredit(gap, now)
        }
        if (wasPlaying && !playback.isPlaying) flushCredit()

        if (previousMs == null) return@guarded // first update after start / host loss: baseline only
        if (intent && !wasIntent) listenEnded = false

        val sameDay = playback.currentDayDate != null && playback.currentDayDate == previousDay
        // A pause command, not a system hold (audio focus, call, buffering keep intent on) and
        // not a switch to another day (loadDay replaces the queue).
        val userStopped = wasIntent && !intent && !playback.hasEnded && sameDay
        val finished = !wasEnded && playback.hasEnded && sameDay
        if ((userStopped || finished) && isInteractive) listenEnded = true
        if (finished && isInteractive && screen == ReviewScreen.EPISODES) {
            onMoment(ReviewMoment.FINISHED_ON_LIST)
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
        // Dry run with nothing left to learn this process: skip the work entirely.
        if (!environment.autoPromptEnabled && loggedWouldRequest) return
        val activeLauncher = launcher
        pending = scope.launch {
            // Warm the platform flow only when the pure gates already pass.
            if (activeLauncher != null && environment.autoPromptEnabled &&
                ReviewPolicy.evaluate(state, context(clock.nowMs(), activeLauncher)) == ReviewDecision.Request
            ) {
                launch { guardedSuspend("prepare") { activeLauncher.prepare() } }
            }
            delay(ReviewPolicy.SETTLE_MS)
            try {
                attempt(moment)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w(TAG, "review attempt failed", e)
            }
        }
    }

    private suspend fun attempt(moment: ReviewMoment) {
        flushCredit()
        val now = clock.nowMs()
        val activeLauncher = launcher
        val enabled = environment.autoPromptEnabled
        val live = context(now, activeLauncher)
        // Dry run: evaluate everything else exactly as if the switch were on.
        val decision = decide(if (enabled) live else live.copy(autoPromptEnabled = true))

        if (decision is ReviewDecision.Skip) {
            AppLog.d(TAG, "skip ${moment.analyticsValue}: ${decision.reason}${if (enabled) "" else " (dry run)"}")
            if (decision.reason.isEngagementGate && !loggedSkip) {
                loggedSkip = true
                log(
                    AnalyticsEvents.IN_APP_REVIEW_SKIPPED,
                    mapOf(
                        AnalyticsEvents.PARAM_MOMENT to moment.analyticsValue,
                        AnalyticsEvents.PARAM_REASON to decision.reason.name.lowercase(),
                        AnalyticsEvents.PARAM_QUALIFYING_DAYS to state.qualifyingDays.size,
                    )
                )
            }
            return
        }
        if (!enabled) {
            AppLog.i(TAG, "dry run ${moment.analyticsValue}: would request")
            if (!loggedWouldRequest) {
                loggedWouldRequest = true
                log(
                    AnalyticsEvents.IN_APP_REVIEW_WOULD_REQUEST,
                    mapOf(
                        AnalyticsEvents.PARAM_MOMENT to moment.analyticsValue,
                        AnalyticsEvents.PARAM_QUALIFYING_DAYS to state.qualifyingDays.size,
                    )
                )
            }
            return
        }
        activeLauncher ?: return // NO_LAUNCHER already skipped; keeps the compiler honest

        // Persist first, read back, and only then ask: a broken store must never let the
        // app ask again and again.
        val before = state
        val qualifying = before.qualifyingDays.size
        val spent = ReviewPolicy.recordRequest(before, now, live.appVersion)
        if (!persistVerified(spent)) {
            AppLog.w(TAG, "skip ${moment.analyticsValue}: attempt did not persist")
            return
        }
        state = spent
        requestedThisProcess = true

        val result = try {
            activeLauncher.launch()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w(TAG, "review launcher failed", e)
            ReviewLaunchResult.UNAVAILABLE
        }

        // The platform never acted: give the attempt back (still no second ask this process).
        when (result) {
            ReviewLaunchResult.CALLED -> Unit
            ReviewLaunchResult.CONTEXT_GONE -> giveBack(before)
            ReviewLaunchResult.UNAVAILABLE -> {
                // Stamp the retry gap onto the spent state first, so a write that fails
                // half-way through the give-back can never lose it.
                giveBack(spent.copy(lastUnavailableMs = now), before.copy(lastUnavailableMs = now))
            }
        }

        log(
            AnalyticsEvents.IN_APP_REVIEW_REQUESTED,
            mapOf(
                AnalyticsEvents.PARAM_MOMENT to moment.analyticsValue,
                AnalyticsEvents.PARAM_REQUEST_NUMBER to spent.requestCount,
                AnalyticsEvents.PARAM_QUALIFYING_DAYS to qualifying,
                AnalyticsEvents.PARAM_RESULT to result.analyticsValue,
            )
        )
        AppLog.i(
            TAG,
            "requested ${moment.analyticsValue}: #${spent.requestCount}, " +
                "$qualifying qualifying days, result=${result.analyticsValue}"
        )
    }

    /** Live facts. Casting is sampled separately, only when it can change the answer. */
    private fun context(now: Long, activeLauncher: ReviewLauncher?): ReviewContext {
        val playback = playbackState.value
        return ReviewContext(
            nowMs = now,
            todayKey = ReviewPolicy.localDayKey(now, clock.timeZone()),
            appVersion = appVersion(),
            autoPromptEnabled = environment.autoPromptEnabled,
            screen = screen,
            isResumed = resumed,
            isWindowFocused = windowFocused,
            canPresent = activeLauncher != null && guarded("canPresent", false) { activeLauncher.canPresent() },
            isBusy = holds.isNotEmpty(),
            foregroundForMs = interactiveSinceMs?.let { (now - it).coerceAtLeast(0L) } ?: 0L,
            isPlaying = playback.isPlaying,
            intendsToPlay = playback.intendsToPlay,
            hasActiveDay = playback.currentDayDate != null,
            isCasting = false,
            lastFailureMs = sessionHealth.lastFailureMs.value,
            requestedThisProcess = requestedThisProcess,
            hasLauncher = activeLauncher != null,
        )
    }

    /**
     * Gates before CASTING don't depend on it, so the Cast framework is only consulted when
     * the answer would otherwise be a request or an engagement skip.
     */
    private fun decide(ctx: ReviewContext): ReviewDecision {
        val first = ReviewPolicy.evaluate(state, ctx)
        if (first is ReviewDecision.Skip && first.reason < SkipReason.CASTING) return first
        val casting = guarded("isCasting", false) { environment.isCasting() }
        return if (casting) ReviewPolicy.evaluate(state, ctx.copy(isCasting = true)) else first
    }

    /** The app version can't change within a process. */
    private fun appVersion(): String =
        cachedAppVersion ?: guarded("appVersion", "unknown") { environment.appVersion() }
            .also { cachedAppVersion = it }

    /** Undo a spent attempt, step by step; each write is verified and a failed one stops there. */
    private fun giveBack(vararg steps: ReviewState) {
        for (step in steps) {
            if (!persistVerified(step)) {
                AppLog.w(TAG, "could not give the attempt back; it stays spent")
                return
            }
            state = step
        }
    }

    private fun persistVerified(next: ReviewState): Boolean = try {
        store.save(next)
        store.load() == next
    } catch (e: Exception) {
        AppLog.w(TAG, "could not persist review state", e)
        false
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

    private fun log(name: String, params: Map<String, Any>) {
        guarded("analytics", Unit) { analytics.logEvent(name, params) }
    }

    private inline fun <T> guarded(what: String, fallback: T, block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppLog.w(TAG, "$what failed", e)
        fallback
    }

    private suspend inline fun guardedSuspend(what: String, crossinline block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w(TAG, "$what failed", e)
        }
    }
}
