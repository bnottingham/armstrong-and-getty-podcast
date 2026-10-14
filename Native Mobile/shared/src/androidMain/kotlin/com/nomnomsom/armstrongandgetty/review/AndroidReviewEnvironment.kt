package com.nomnomsom.armstrongandgetty.review

import android.content.Context
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastState
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.nomnomsom.armstrongandgetty.util.AppLog

/**
 * Android auto-prompt switch. ON since 2026-10-14 (Brett's call: ship the prompt on both
 * platforms now, rather than waiting for the Wi-Fi-only download setting that
 * seo/strategy.md §4.4 suggested first). Setting it to `false` turns every moment into a dry
 * run that only logs `in_app_review_would_request` / `in_app_review_skipped`.
 * Known gap: only the phone UI's PlaybackController feeds the listening tracker, so Android
 * Auto-only listening earns no qualifying days (an undercount, so it never over-asks).
 */
const val ANDROID_AUTO_PROMPT_ENABLED = true

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

    /**
     * Main thread only. The no-argument getter returns the instance AGPodcastApp / MainActivity
     * already initialized, or null; it never (re)attempts initialization, so this stays cheap
     * on devices whose Play services are missing or broken (→ not casting).
     */
    override fun isCasting(): Boolean = try {
        CastContext.getSharedInstance()?.castState == CastState.CONNECTED
    } catch (_: Exception) {
        false
    }

    private companion object {
        const val TAG = "InAppReview"
    }
}
