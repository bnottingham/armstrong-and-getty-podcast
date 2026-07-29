//
//  FirebaseBridge.swift
//  armstrongandgetty
//
//  Thin bridge implementing the shared AnalyticsTracker interface against the
//  official Firebase SDK. All event definitions and call sites live in Kotlin;
//  this only forwards. Crash capture itself needs no bridge — FirebaseApp.configure()
//  activates Crashlytics' native crash handlers.
//

import Foundation
import FirebaseAnalytics
import FirebaseCrashlytics
import Shared

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
