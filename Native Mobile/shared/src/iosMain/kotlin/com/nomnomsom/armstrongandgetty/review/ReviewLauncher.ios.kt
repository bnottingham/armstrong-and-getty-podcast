package com.nomnomsom.armstrongandgetty.review

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** StoreKit via the Swift bridge. Missing bridge → UNAVAILABLE (never a crash). */
@Composable
actual fun rememberReviewLauncher(): ReviewLauncher = remember {
    ReviewLauncher {
        val bridge = StoreReviewBridgeHolder.bridge ?: return@ReviewLauncher ReviewLaunchResult.UNAVAILABLE
        if (bridge.requestReview()) ReviewLaunchResult.CALLED else ReviewLaunchResult.CONTEXT_GONE
    }
}
