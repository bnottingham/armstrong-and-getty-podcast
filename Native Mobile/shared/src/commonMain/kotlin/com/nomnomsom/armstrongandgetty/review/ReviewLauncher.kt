package com.nomnomsom.armstrongandgetty.review

import androidx.compose.runtime.Composable

/**
 * What the platform call reported. Neither store says whether a sheet was actually shown or
 * what the listener did, so nothing here (or in analytics) ever claims "shown" or "rated".
 */
enum class ReviewLaunchResult(val analyticsValue: String) {
    /** The system review API was called. The attempt counts. */
    CALLED("called"),

    /** No foreground Activity / window scene to present from. Nothing was invoked; given back. */
    CONTEXT_GONE("context_gone"),

    /**
     * The platform flow failed or isn't available (no Play Store, Play error, bridge missing).
     * Given back, with a [ReviewPolicy.UNAVAILABLE_RETRY_MS] retry gap.
     */
    UNAVAILABLE("unavailable"),
}

/** Performs the ONE platform review call. Only [ReviewCoordinator] may invoke it. */
interface ReviewLauncher {
    /**
     * Cheap main-thread check, made BEFORE an attempt is spent: could the platform present
     * right now? Android: Activity resumed, not finishing, window focused. iOS: bridge set and
     * a foreground-active window scene exists.
     */
    fun canPresent(): Boolean

    /**
     * Warm-up during the settle delay; never shows anything. Android prefetches ReviewInfo
     * (Google's recommendation) so the window between spending the attempt and launching the
     * flow stays minimal.
     */
    suspend fun prepare() {}

    suspend fun launch(): ReviewLaunchResult
}

/**
 * Launcher bound to the current host: Android → Play In-App Review against LocalActivity;
 * iOS → AppStore.requestReview(in:) through the Swift StoreReviewBridge.
 */
@Composable
expect fun rememberReviewLauncher(): ReviewLauncher
