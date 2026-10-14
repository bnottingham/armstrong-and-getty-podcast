package com.nomnomsom.armstrongandgetty.review

import androidx.compose.runtime.Composable

/**
 * What the platform call reported. Neither store says whether a sheet was actually shown or
 * what the listener did, so nothing here (or in analytics) ever claims "shown" or "rated".
 */
enum class ReviewLaunchResult(val analyticsValue: String) {
    /** The system review API was called. */
    CALLED("called"),

    /** No foreground Activity / window scene to present from. */
    CONTEXT_GONE("context_gone"),

    /** The platform flow failed or isn't available (e.g. no Play Store, bridge missing). */
    UNAVAILABLE("unavailable"),
}

/** Performs the ONE platform review call. Only [ReviewCoordinator] may invoke it. */
fun interface ReviewLauncher {
    suspend fun launch(): ReviewLaunchResult
}

/**
 * Launcher bound to the current host: Android → Play In-App Review against LocalActivity;
 * iOS → AppStore.requestReview(in:) through the Swift StoreReviewBridge.
 */
@Composable
expect fun rememberReviewLauncher(): ReviewLauncher
