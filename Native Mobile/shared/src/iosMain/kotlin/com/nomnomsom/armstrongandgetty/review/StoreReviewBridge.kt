package com.nomnomsom.armstrongandgetty.review

/**
 * Implemented in Swift (AppStoreReviewBridge in iOS/FirebaseBridge.swift): StoreKit's
 * `AppStore.requestReview(in:)` and Crashlytics' `didCrashDuringPreviousExecution()` are
 * only reachable from Swift. Kotlin decides whether and when to ask; Swift only performs
 * the call. Main thread only.
 */
interface StoreReviewBridge {
    /** Finds the foreground-active UIWindowScene and calls AppStore.requestReview(in:); false if none. */
    fun requestReview(): Boolean

    fun didCrashOnPreviousRun(): Boolean
}

object StoreReviewBridgeHolder {
    var bridge: StoreReviewBridge? = null
}

/** Called from Swift (armstrongandgettyApp.init) — Swift: `StoreReviewBridgeKt.setStoreReviewBridge(bridge:)`. */
fun setStoreReviewBridge(bridge: StoreReviewBridge) {
    StoreReviewBridgeHolder.bridge = bridge
}
