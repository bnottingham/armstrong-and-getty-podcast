package com.nomnomsom.armstrongandgetty.review

import androidx.compose.runtime.Composable
import platform.UIKit.UIApplication
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UIWindowScene

@Composable
actual fun rememberReviewLauncher(): ReviewLauncher = AppStoreReviewLauncher

/** StoreKit via the Swift bridge. Missing bridge → UNAVAILABLE (never a crash). Main thread only. */
private object AppStoreReviewLauncher : ReviewLauncher {
    override fun canPresent(): Boolean =
        StoreReviewBridgeHolder.bridge != null && hasForegroundActiveScene()

    override suspend fun launch(): ReviewLaunchResult {
        val bridge = StoreReviewBridgeHolder.bridge ?: return ReviewLaunchResult.UNAVAILABLE
        return if (bridge.requestReview()) ReviewLaunchResult.CALLED else ReviewLaunchResult.CONTEXT_GONE
    }
}

/** Same test the Swift bridge makes before AppStore.requestReview(in:). */
private fun hasForegroundActiveScene(): Boolean =
    UIApplication.sharedApplication.connectedScenes.any {
        (it as? UIWindowScene)?.activationState == UISceneActivationStateForegroundActive
    }
