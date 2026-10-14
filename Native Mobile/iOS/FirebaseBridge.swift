//
//  FirebaseBridge.swift
//  armstrongandgetty
//
//  Thin Swift bridges for shared Kotlin interfaces (Firebase analytics, App Store review).
//  All event definitions, call sites and review gating live in Kotlin; these only
//  forward. Crash capture itself needs no bridge — FirebaseApp.configure() activates
//  Crashlytics' native crash handlers.
//

import Foundation
import FirebaseAnalytics
import FirebaseCrashlytics
import Shared
import StoreKit
import UIKit

final class FirebaseAnalyticsBridge: NSObject, AnalyticsTracker {
    func logEvent(name: String, params: [String: Any]) {
        Analytics.logEvent(name, parameters: params.isEmpty ? nil : params)
    }

    func logScreen(screenName: String) {
        Analytics.logEvent(AnalyticsEventScreenView, parameters: [
            AnalyticsParameterScreenName: screenName
        ])
    }

    func setUserProperty(name: String, value: String?) {
        Analytics.setUserProperty(value, forName: name)
    }

    func recordError(message: String, throwable: KotlinThrowable?) {
        Crashlytics.crashlytics().log(message)
        let detail = throwable?.message ?? message
        let error = NSError(
            domain: "com.nomnomsom.armstrongandgetty.AppError",
            code: 0,
            userInfo: [NSLocalizedDescriptionKey: detail]
        )
        Crashlytics.crashlytics().record(error: error)
    }
}

/// Shared Kotlin code decides *whether* and *when* to ask (ReviewPolicy); this only performs the one
/// StoreKit call. AppStore.requestReview(in:) is Swift-only, hence the bridge. No-op in TestFlight.
final class AppStoreReviewBridge: NSObject, StoreReviewBridge {
    func requestReview() -> Bool {
        guard let scene = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene })
            .first(where: { $0.activationState == .foregroundActive }) else { return false }
        AppStore.requestReview(in: scene)
        return true
    }

    func didCrashOnPreviousRun() -> Bool {
        Crashlytics.crashlytics().didCrashDuringPreviousExecution()
    }
}
