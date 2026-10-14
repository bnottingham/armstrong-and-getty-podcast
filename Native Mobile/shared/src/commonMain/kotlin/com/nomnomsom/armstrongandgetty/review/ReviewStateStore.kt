package com.nomnomsom.armstrongandgetty.review

import com.russhwolf.settings.Settings
import kotlinx.datetime.LocalDate

/**
 * Settings-backed persistence for [ReviewState]. Reads are tolerant (a wrong type or a
 * malformed day falls back to the default) and writes touch ONLY [ReviewKeys.all].
 */
class ReviewStateStore(private val settings: Settings) {

    fun load(): ReviewState = ReviewState(
        firstSeenMs = read { settings.getLongOrNull(ReviewKeys.FIRST_SEEN_MS) },
        listenDayKey = read { settings.getStringOrNull(ReviewKeys.LISTEN_DAY) }?.let(::normalizeDayKey),
        listenDayMs = read { settings.getLongOrNull(ReviewKeys.LISTEN_DAY_MS) }?.coerceAtLeast(0L) ?: 0L,
        qualifyingDays = parseDays(read { settings.getStringOrNull(ReviewKeys.QUALIFYING_DAYS) }),
        lastRequestMs = read { settings.getLongOrNull(ReviewKeys.LAST_REQUEST_MS) },
        requestCount = read { settings.getIntOrNull(ReviewKeys.REQUEST_COUNT) }?.coerceAtLeast(0) ?: 0,
        lastRequestVersion = read { settings.getStringOrNull(ReviewKeys.LAST_REQUEST_VERSION) },
        lastCrashMs = read { settings.getLongOrNull(ReviewKeys.LAST_CRASH_MS) },
        lastUnavailableMs = read { settings.getLongOrNull(ReviewKeys.LAST_UNAVAILABLE_MS) },
    )

    fun save(state: ReviewState) {
        putLong(ReviewKeys.FIRST_SEEN_MS, state.firstSeenMs)
        putString(ReviewKeys.LISTEN_DAY, state.listenDayKey)
        settings.putLong(ReviewKeys.LISTEN_DAY_MS, state.listenDayMs)
        putString(
            ReviewKeys.QUALIFYING_DAYS,
            state.qualifyingDays.takeIf { it.isNotEmpty() }?.joinToString(",")
        )
        putLong(ReviewKeys.LAST_REQUEST_MS, state.lastRequestMs)
        settings.putInt(ReviewKeys.REQUEST_COUNT, state.requestCount)
        putString(ReviewKeys.LAST_REQUEST_VERSION, state.lastRequestVersion)
        putLong(ReviewKeys.LAST_CRASH_MS, state.lastCrashMs)
        putLong(ReviewKeys.LAST_UNAVAILABLE_MS, state.lastUnavailableMs)
    }

    private fun putLong(key: String, value: Long?) {
        if (value == null) settings.remove(key) else settings.putLong(key, value)
    }

    private fun putString(key: String, value: String?) {
        if (value == null) settings.remove(key) else settings.putString(key, value)
    }

    private inline fun <T> read(block: () -> T?): T? = try {
        block()
    } catch (_: Exception) {
        null
    }

    internal companion object {
        /** Valid yyyy-MM-dd keys only, deduplicated, sorted, newest [ReviewPolicy.MAX_STORED_DAYS]. */
        fun parseDays(raw: String?): List<String> =
            raw.orEmpty()
                .split(',')
                .mapNotNull { normalizeDayKey(it.trim()) }
                .distinct()
                .sorted()
                .takeLast(ReviewPolicy.MAX_STORED_DAYS)

        fun normalizeDayKey(raw: String): String? =
            if (raw.isEmpty()) null else runCatching { LocalDate.parse(raw).toString() }.getOrNull()
    }
}
