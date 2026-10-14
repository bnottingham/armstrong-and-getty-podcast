package com.nomnomsom.armstrongandgetty.review

/**
 * Persistent keys for the in-app rating prompt. All NEW: Android writes them to its own
 * SharedPreferences file ([ANDROID_PREFS_FILE]); iOS writes them to standard NSUserDefaults
 * under the `in_app_review.` prefix. Upgrade-in-place storage (prefs file `user_deletions`
 * / key `dates`, NSUserDefaults `user_deletions_dates`, Room `ag_podcast_db`, `podcasts/`)
 * is never read or written. ReviewStateStoreTest enforces this.
 */
object ReviewKeys {
    /** Android SharedPreferences file name. Must never equal `user_deletions`. */
    const val ANDROID_PREFS_FILE = "in_app_review"

    const val PREFIX = "in_app_review."

    const val FIRST_SEEN_MS = "in_app_review.first_seen_ms"
    const val LISTEN_DAY = "in_app_review.listen_day"
    const val LISTEN_DAY_MS = "in_app_review.listen_day_ms"
    const val QUALIFYING_DAYS = "in_app_review.qualifying_days"
    const val LAST_REQUEST_MS = "in_app_review.last_request_ms"
    const val REQUEST_COUNT = "in_app_review.request_count"
    const val LAST_REQUEST_VERSION = "in_app_review.last_request_version"
    const val LAST_CRASH_MS = "in_app_review.last_crash_ms"

    val all: List<String> = listOf(
        FIRST_SEEN_MS,
        LISTEN_DAY,
        LISTEN_DAY_MS,
        QUALIFYING_DAYS,
        LAST_REQUEST_MS,
        REQUEST_COUNT,
        LAST_REQUEST_VERSION,
        LAST_CRASH_MS,
    )
}
