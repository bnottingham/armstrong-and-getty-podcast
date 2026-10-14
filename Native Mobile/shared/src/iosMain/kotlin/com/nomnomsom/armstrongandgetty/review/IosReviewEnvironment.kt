package com.nomnomsom.armstrongandgetty.review

import googlecast.GCKCastContext
import googlecast.GCKCastStateConnected
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSBundle

/**
 * Referenced only from platformModule(), never from tests, so the GoogleCast symbols stay
 * out of the bare test executable (same as MediaRouteAction.ios.kt).
 */
@OptIn(ExperimentalForeignApi::class)
class IosReviewEnvironment : ReviewEnvironment {

    override val autoPromptEnabled: Boolean = true

    override fun appVersion(): String =
        NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String
            ?: "unknown"

    override fun crashedOnPreviousRun(): Boolean =
        StoreReviewBridgeHolder.bridge?.didCrashOnPreviousRun() ?: false

    /** Main thread only. Checks initialization first: sharedInstance() raises if Cast never set up. */
    override fun isCasting(): Boolean = runCatching {
        GCKCastContext.isSharedInstanceInitialized() &&
            GCKCastContext.sharedInstance().castState == GCKCastStateConnected
    }.getOrDefault(false)
}
