package com.nomnomsom.armstrongandgetty.review

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Everything the in-app rating prompt remembers between launches. Persisted by
 * [ReviewStateStore] under brand-new keys ([ReviewKeys]); never touches existing storage.
 */
data class ReviewState(
    /** First time [ReviewCoordinator.start] ran in a build with this feature. */
    val firstSeenMs: Long? = null,
    /** Device-local day (yyyy-MM-dd) whose listening time is accumulating in [listenDayMs]. */
    val listenDayKey: String? = null,
    val listenDayMs: Long = 0L,
    /** Local days with ≥ [ReviewPolicy.MIN_LISTEN_MS_PER_DAY] credited listening; sorted asc, distinct, capped. */
    val qualifyingDays: List<String> = emptyList(),
    val lastRequestMs: Long? = null,
    val requestCount: Int = 0,
    val lastRequestVersion: String? = null,
    /** When a crash on the previous run was detected (holds the prompt for [ReviewPolicy.CRASH_QUIET_MS]). */
    val lastCrashMs: Long? = null,
    /**
     * When the platform review flow was last unavailable (no Play Store, Play error, bridge
     * missing). Such an attempt is given back, but holds the next one for
     * [ReviewPolicy.UNAVAILABLE_RETRY_MS].
     */
    val lastUnavailableMs: Long? = null,
)

enum class ReviewScreen { EPISODES, PLAYER, ABOUT, OTHER }

/**
 * The only two moments at which a request is ever evaluated. Both mark the END of a listen
 * the listener made in this foreground visit; neither is the direct result of a tap (Apple:
 * don't request "in response to a button tap or other user action").
 */
enum class ReviewMoment(val analyticsValue: String) {
    /**
     * Route went from `player/{date}` back to `episodes` after the listener stopped (paused
     * or played to the end) the day they were on, during this player visit, while the app
     * was in the foreground. Opening the player and backing out, peeking at another day, a
     * stop from the lock screen / notification / Auto, or audio held back by a call or
     * another app never qualifies.
     */
    LEFT_PLAYER("left_player"),

    /** The day played through to its natural end while the Episodes list was on screen. */
    FINISHED_ON_LIST("finished_on_list"),
}

/** Live, in-process facts sampled at evaluation time (after the settle delay). */
data class ReviewContext(
    val nowMs: Long,
    /** Device-local day key — deliberately NOT the episode engine's UTC day key. */
    val todayKey: String,
    val appVersion: String,
    val autoPromptEnabled: Boolean,
    val screen: ReviewScreen,
    /** Lifecycle RESUMED (iOS: scene foreground-active). */
    val isResumed: Boolean,
    /** Window focus: false under the Android notification shade, a dialog window or the Cast chooser. */
    val isWindowFocused: Boolean,
    /** The launcher reports the platform could present right now (Activity / active scene). */
    val canPresent: Boolean,
    /** Something on screen needs the listener: a dialog, a refresh spinner, a failed download. */
    val isBusy: Boolean,
    /** How long the host has been resumed AND focused without a break (0 when it isn't). */
    val foregroundForMs: Long,
    val isPlaying: Boolean,
    /** [com.nomnomsom.armstrongandgetty.media.PlaybackState.intendsToPlay]: true while playback is only held back. */
    val intendsToPlay: Boolean,
    val hasActiveDay: Boolean,
    val isCasting: Boolean,
    /** Last user-visible failure in this process that hasn't been followed by a success. */
    val lastFailureMs: Long?,
    val requestedThisProcess: Boolean,
    val hasLauncher: Boolean,
)

/** Why an evaluation declined to ask. Order matches [ReviewPolicy.evaluate]. */
enum class SkipReason {
    DISABLED,
    ALREADY_THIS_PROCESS,
    NO_LAUNCHER,
    NOT_FOREGROUND,
    NO_WINDOW_FOCUS,
    CANNOT_PRESENT,
    NOT_HOME_SCREEN,
    BUSY,
    JUST_OPENED,
    PLAYING,
    NO_ACTIVE_DAY,
    CASTING,
    SESSION_FAILURE,
    TOO_NEW,
    CLOCK_SKEW,
    RECENT_CRASH,
    LIFETIME_CAP,
    VERSION_ALREADY_ASKED,
    COOLDOWN,
    RETRY_COOLDOWN,
    TODAY_NOT_QUALIFIED,
    NEEDS_LISTENING_DAYS,
    ;

    /**
     * Engagement / health gates: the moment itself was a good one, the listener just hasn't
     * earned an ask yet. These (only) are reported as `in_app_review_skipped`.
     */
    val isEngagementGate: Boolean
        get() = when (this) {
            SESSION_FAILURE, TOO_NEW, RECENT_CRASH, COOLDOWN, TODAY_NOT_QUALIFIED, NEEDS_LISTENING_DAYS -> true
            else -> false
        }
}

sealed interface ReviewDecision {
    data object Request : ReviewDecision
    data class Skip(val reason: SkipReason) : ReviewDecision
}

/**
 * Pure gating policy for the in-app rating prompt: no I/O, no clock of its own.
 *
 * Neither store reports whether a sheet appeared or what the listener did, so every
 * request the platform accepted spends an attempt and the caps stand in for "stop after N
 * declines". An attempt the platform never acted on (no presentable window, review flow
 * unavailable) is given back. See CLAUDE.md "In-app review" for the compliance rules.
 */
object ReviewPolicy {
    const val DAY_MS = 86_400_000L

    /** Credited listening a local day needs before it counts (10 min). */
    const val MIN_LISTEN_MS_PER_DAY = 600_000L

    /** Distinct qualifying local days needed (counted since the last request). */
    const val MIN_QUALIFYING_DAYS = 3

    /** Elapsed time since [ReviewState.firstSeenMs] before the first ask. */
    const val MIN_INSTALL_AGE_MS = 5 * DAY_MS

    /** Gap after any attempt. 120 × 3 fits Apple's 3-per-365-days ceiling. */
    const val COOLDOWN_MS = 120 * DAY_MS

    /** At most one attempt per app version (enforced via [ReviewState.lastRequestVersion]). */
    const val MAX_PER_VERSION = 1

    /** Lifetime attempts. */
    const val LIFETIME_CAP = 3

    /** Quiet period after a crash on the previous run was detected. */
    const val CRASH_QUIET_MS = 7 * DAY_MS

    /** Retry gap after the platform review flow was unavailable (the attempt itself is given back). */
    const val UNAVAILABLE_RETRY_MS = 14 * DAY_MS

    /** A feed / download failure the listener saw holds the prompt this long (or until a success). */
    const val FAILURE_QUIET_MS = 30 * 60_000L

    /** The app must have been in the foreground (resumed and focused) this long: never "just opened". */
    const val MIN_FOREGROUND_MS = 30_000L

    /** Delay between a natural moment and the (re-checked) evaluation. */
    const val SETTLE_MS = 2_000L

    /**
     * Longest playback-state interval that earns credit (the ticker is 500 ms). A longer gap
     * means the controller was disconnected or stalled, and earns nothing.
     */
    const val MAX_TICK_CREDIT_MS = 2_000L

    /** Persist accumulated credit at most this often while playing. */
    const val FLUSH_INTERVAL_MS = 30_000L

    /** Bounded storage for [ReviewState.qualifyingDays]; only the count matters. */
    const val MAX_STORED_DAYS = 16

    /**
     * The listener's own calendar date (yyyy-MM-dd) in [zone]. Not the engine's UTC day key
     * (PodcastRepository.todayKey): the engine groups shows by UTC airing date, while this
     * measures a listening habit, which follows the listener's midnight.
     */
    fun localDayKey(nowMs: Long, zone: TimeZone): String =
        Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(zone).date.toString()

    fun recordFirstSeen(state: ReviewState, nowMs: Long): ReviewState =
        if (state.firstSeenMs != null) state else state.copy(firstSeenMs = nowMs)

    fun recordCrash(state: ReviewState, nowMs: Long): ReviewState =
        state.copy(lastCrashMs = nowMs)

    /**
     * Pulls stamps recorded while the device clock ran ahead back to [nowMs]. Otherwise a
     * corrected clock would hit CLOCK_SKEW on every evaluation until real time caught up
     * (possibly years). Conservative: each affected wait restarts from now.
     */
    fun clampFutureTimestamps(state: ReviewState, nowMs: Long): ReviewState {
        fun Long?.clamp() = this?.coerceAtMost(nowMs)
        val clamped = state.copy(
            firstSeenMs = state.firstSeenMs.clamp(),
            lastRequestMs = state.lastRequestMs.clamp(),
            lastCrashMs = state.lastCrashMs.clamp(),
            lastUnavailableMs = state.lastUnavailableMs.clamp(),
        )
        return if (clamped == state) state else clamped
    }

    /**
     * Adds [creditMs] of listening to [todayKey]. A new day resets the accumulator; the day
     * joins [ReviewState.qualifyingDays] once (sorted, newest [MAX_STORED_DAYS] kept) when its
     * total reaches [MIN_LISTEN_MS_PER_DAY].
     */
    fun recordListening(state: ReviewState, creditMs: Long, todayKey: String): ReviewState {
        if (creditMs <= 0L) return state
        val base = if (state.listenDayKey == todayKey) state.listenDayMs else 0L
        val total = base + creditMs
        val days = if (total >= MIN_LISTEN_MS_PER_DAY && todayKey !in state.qualifyingDays) {
            (state.qualifyingDays + todayKey).distinct().sorted().takeLast(MAX_STORED_DAYS)
        } else {
            state.qualifyingDays
        }
        return state.copy(listenDayKey = todayKey, listenDayMs = total, qualifyingDays = days)
    }

    /** First failing gate wins; all time boundaries are inclusive. */
    fun evaluate(state: ReviewState, ctx: ReviewContext): ReviewDecision {
        fun skip(reason: SkipReason) = ReviewDecision.Skip(reason)
        val now = ctx.nowMs

        if (!ctx.autoPromptEnabled) return skip(SkipReason.DISABLED)
        if (ctx.requestedThisProcess) return skip(SkipReason.ALREADY_THIS_PROCESS)
        if (!ctx.hasLauncher) return skip(SkipReason.NO_LAUNCHER)
        if (!ctx.isResumed) return skip(SkipReason.NOT_FOREGROUND)
        if (!ctx.isWindowFocused) return skip(SkipReason.NO_WINDOW_FOCUS)
        if (!ctx.canPresent) return skip(SkipReason.CANNOT_PRESENT)
        if (ctx.screen != ReviewScreen.EPISODES) return skip(SkipReason.NOT_HOME_SCREEN)
        if (ctx.isBusy) return skip(SkipReason.BUSY)
        if (ctx.foregroundForMs < MIN_FOREGROUND_MS) return skip(SkipReason.JUST_OPENED)
        if (ctx.isPlaying || ctx.intendsToPlay) return skip(SkipReason.PLAYING)
        if (!ctx.hasActiveDay) return skip(SkipReason.NO_ACTIVE_DAY)
        if (ctx.isCasting) return skip(SkipReason.CASTING)
        // A clock that moved back (now < stamp) also reads as "recent": conservative.
        val lastFailure = ctx.lastFailureMs
        if (lastFailure != null && now - lastFailure < FAILURE_QUIET_MS) return skip(SkipReason.SESSION_FAILURE)

        val firstSeen = state.firstSeenMs ?: return skip(SkipReason.TOO_NEW)
        val lastRequest = state.lastRequestMs
        val lastCrash = state.lastCrashMs
        val lastUnavailable = state.lastUnavailableMs
        if (now < firstSeen ||
            (lastRequest != null && now < lastRequest) ||
            (lastCrash != null && now < lastCrash) ||
            (lastUnavailable != null && now < lastUnavailable)
        ) {
            return skip(SkipReason.CLOCK_SKEW)
        }
        if (now - firstSeen < MIN_INSTALL_AGE_MS) return skip(SkipReason.TOO_NEW)
        if (lastCrash != null && now - lastCrash < CRASH_QUIET_MS) return skip(SkipReason.RECENT_CRASH)
        if (state.requestCount >= LIFETIME_CAP) return skip(SkipReason.LIFETIME_CAP)
        if (state.lastRequestVersion == ctx.appVersion) return skip(SkipReason.VERSION_ALREADY_ASKED)
        if (lastRequest != null && now - lastRequest < COOLDOWN_MS) return skip(SkipReason.COOLDOWN)
        if (lastUnavailable != null && now - lastUnavailable < UNAVAILABLE_RETRY_MS) {
            return skip(SkipReason.RETRY_COOLDOWN)
        }
        if (ctx.todayKey !in state.qualifyingDays) return skip(SkipReason.TODAY_NOT_QUALIFIED)
        if (state.qualifyingDays.size < MIN_QUALIFYING_DAYS) return skip(SkipReason.NEEDS_LISTENING_DAYS)
        return ReviewDecision.Request
    }

    /** Spends an attempt and makes the next one earn engagement from scratch. */
    fun recordRequest(state: ReviewState, nowMs: Long, appVersion: String): ReviewState =
        state.copy(
            lastRequestMs = nowMs,
            requestCount = state.requestCount + 1,
            lastRequestVersion = appVersion,
            qualifyingDays = emptyList(),
            listenDayKey = null,
            listenDayMs = 0L,
        )
}
