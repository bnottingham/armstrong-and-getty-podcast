package com.nomnomsom.armstrongandgetty.analytics

/**
 * iOS analytics indirection: the Swift side implements [AnalyticsTracker] against the
 * official Firebase SDK (SPM) and installs it here at launch, before Koin resolves
 * anything. Falls back to a no-op so previews/tests never crash.
 */
object AnalyticsBridgeHolder {
    var tracker: AnalyticsTracker = NoopAnalyticsTracker
}

/** Called from Swift (armstrongandgettyApp.init) with the Firebase-backed implementation. */
fun setAnalyticsTracker(tracker: AnalyticsTracker) {
    AnalyticsBridgeHolder.tracker = tracker
}
