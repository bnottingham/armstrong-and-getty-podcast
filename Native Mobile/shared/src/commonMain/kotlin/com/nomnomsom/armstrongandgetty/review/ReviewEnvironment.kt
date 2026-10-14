package com.nomnomsom.armstrongandgetty.review

import com.nomnomsom.armstrongandgetty.util.AppLog
import kotlinx.datetime.TimeZone
import com.nomnomsom.armstrongandgetty.util.nowMs as wallClockNowMs

/** Platform facts the review coordinator samples. Every call is wrapped by the caller. */
interface ReviewEnvironment {
    /** Whether natural moments may request the system sheet at all on this platform. */
    val autoPromptEnabled: Boolean

    /** User-visible app version (versionName / CFBundleShortVersionString). */
    fun appVersion(): String

    /** Crashlytics: did the previous run of the app crash? */
    fun crashedOnPreviousRun(): Boolean

    /** Whether a Cast session is connected right now (main thread). */
    fun isCasting(): Boolean
}

/** Wall clock + zone, injectable for tests. */
interface ReviewClock {
    fun nowMs(): Long
    fun timeZone(): TimeZone

    companion object {
        val System: ReviewClock = SystemReviewClock()
    }
}

/**
 * The listening tracker asks for the zone on every playback tick (twice a second), and on
 * Darwin `currentSystemDefault()` resets and re-reads the tz database each call. Resolve at
 * most once a minute (a zone change takes effect within a minute) and fall back to the last
 * good zone, then UTC, if resolution throws. Main thread only.
 */
private class SystemReviewClock : ReviewClock {
    private var zone: TimeZone? = null
    private var resolvedAtMs = 0L

    override fun nowMs(): Long = wallClockNowMs()

    override fun timeZone(): TimeZone {
        val now = nowMs()
        val cached = zone
        if (cached != null && now - resolvedAtMs in 0 until ZONE_REFRESH_MS) return cached
        val fresh = try {
            TimeZone.currentSystemDefault()
        } catch (e: Exception) {
            AppLog.w("InAppReview", "system time zone unavailable", e)
            cached ?: TimeZone.UTC
        }
        zone = fresh
        resolvedAtMs = now
        return fresh
    }

    private companion object {
        const val ZONE_REFRESH_MS = 60_000L
    }
}
