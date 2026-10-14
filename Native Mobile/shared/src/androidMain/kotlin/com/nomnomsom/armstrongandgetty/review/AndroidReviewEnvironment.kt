package com.nomnomsom.armstrongandgetty.review

import android.content.Context
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastState
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.nomnomsom.armstrongandgetty.util.AppLog

/**
 * Android auto-prompt switch. Ships OFF: seo/strategy.md §4.4 (P2) and open question 11 —
 * new hours download on any network today, so prompting before a Wi-Fi-only download
 * setting exists invites "ate my data" reviews. Everything else is built, tested and
 * already counting listening days; flip this to `true` once that setting ships and the
 * prompt takes effect immediately. The About "Rate the App" row works regardless.
 */
const val ANDROID_AUTO_PROMPT_ENABLED = false

class AndroidReviewEnvironment(private val context: Context) : ReviewEnvironment {

    override val autoPromptEnabled: Boolean = ANDROID_AUTO_PROMPT_ENABLED

    override fun appVersion(): String = try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
    } catch (e: Exception) {
        AppLog.w(TAG, "versionName unavailable", e)
        "unknown"
    }

    override fun crashedOnPreviousRun(): Boolean = try {
        FirebaseCrashlytics.getInstance().didCrashOnPreviousExecution()
    } catch (e: Exception) {
        AppLog.w(TAG, "Crashlytics unavailable", e)
        false
    }

    /** Main thread only (CastContext). No Play services → not casting. */
    override fun isCasting(): Boolean = try {
        @Suppress("DEPRECATION")
        CastContext.getSharedInstance(context).castState == CastState.CONNECTED
    } catch (_: Exception) {
        false
    }

    private companion object {
        const val TAG = "InAppReview"
    }
}
