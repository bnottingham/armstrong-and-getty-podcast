package com.nomnomsom.armstrongandgetty.review

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
        val System: ReviewClock = object : ReviewClock {
            override fun nowMs(): Long = wallClockNowMs()
            override fun timeZone(): TimeZone = TimeZone.currentSystemDefault()
        }
    }
}
