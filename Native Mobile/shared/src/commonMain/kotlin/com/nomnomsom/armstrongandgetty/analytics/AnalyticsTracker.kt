package com.nomnomsom.armstrongandgetty.analytics

/**
 * App-wide analytics facade. Common code logs events through this; the platform
 * implementations forward to Firebase Analytics (Android directly, iOS via a thin
 * Swift bridge handed to Koin at startup). Event and parameter names follow
 * Firebase conventions: snake_case, ≤40 chars.
 */
interface AnalyticsTracker {
    fun logEvent(name: String, params: Map<String, Any> = emptyMap())
    fun logScreen(screenName: String)
    fun setUserProperty(name: String, value: String?)

    /** Non-fatal error reporting → Crashlytics. */
    fun recordError(message: String, throwable: Throwable? = null)
}

/** No-op fallback so the app never crashes for lack of a bridge (e.g. previews/tests). */
object NoopAnalyticsTracker : AnalyticsTracker {
    override fun logEvent(name: String, params: Map<String, Any>) {}
    override fun logScreen(screenName: String) {}
    override fun setUserProperty(name: String, value: String?) {}
    override fun recordError(message: String, throwable: Throwable?) {}
}

/** Central catalog of event names so the taxonomy stays discoverable and consistent. */
object AnalyticsEvents {
    const val EPISODE_PLAY = "episode_play"
    const val EPISODE_PAUSE = "episode_pause"
    const val EPISODE_DOWNLOAD = "episode_download"
    const val DOWNLOAD_COMPLETED = "download_completed"
    const val DOWNLOAD_FAILED = "download_failed"
    const val DOWNLOAD_CANCELLED = "download_cancelled"
    const val SEGMENT_RETRY = "segment_retry"
    const val EPISODE_DELETE = "episode_delete"
    const val EPISODE_RESTART = "episode_restart"
    const val SEEK = "seek"
    const val SPEED_CHANGE = "speed_change"
    const val SEGMENT_JUMP = "segment_jump"
    const val FEED_REFRESH_FAILED = "feed_refresh_failed"
    const val BACKGROUND_REFRESH = "background_refresh"
    const val ABOUT_LINK_OPEN = "about_link_open"

    /**
     * The app asked the platform for its review flow. Never means "shown" or "rated".
     * `result` = called | context_gone | unavailable (the last two are given back).
     */
    const val IN_APP_REVIEW_REQUESTED = "in_app_review_requested"

    /** At most once per process: a good moment, but an engagement / health gate said not yet. */
    const val IN_APP_REVIEW_SKIPPED = "in_app_review_skipped"

    /** At most once per process, auto-prompt switched off (Android): the policy would have asked. */
    const val IN_APP_REVIEW_WOULD_REQUEST = "in_app_review_would_request"

    // Params
    const val PARAM_DATE = "episode_date"
    const val PARAM_SEGMENT_INDEX = "segment_index"
    const val PARAM_SPEED = "speed"
    const val PARAM_DELTA_MS = "delta_ms"
    const val PARAM_REASON = "reason"
    const val PARAM_NEW_SEGMENTS = "new_segments"
    const val PARAM_RESULT = "result"
    const val PARAM_LINK = "link"
    const val PARAM_MOMENT = "moment"
    const val PARAM_REQUEST_NUMBER = "request_number"
    const val PARAM_QUALIFYING_DAYS = "qualifying_days"
}
