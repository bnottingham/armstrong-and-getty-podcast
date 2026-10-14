package com.nomnomsom.armstrongandgetty.review

import android.content.Context
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastState
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.nomnomsom.armstrongandgetty.util.AppLog

/**
 * Android auto-prompt switch. Ships OFF: seo/strategy.md §4.4 (P2) and open question 11 —
 * new hours download on any network today, so prompting before a Wi-Fi-only download
 * setting exists invites "ate my data" reviews. Meanwhile every moment is a dry run that
 * logs `in_app_review_would_request` / `in_app_review_skipped` (nothing is spent or shown).
 * The About "Rate the App" row works regardless.
 *
 * Before flipping this to `true`:
 * - ship the Wi-Fi-only download setting;
 * - credit listening from PlaybackService too: today only the phone UI's PlaybackController
 *   feeds the tracker, so Android Auto-only listening earns no qualifying days;
 * - check the dry-run counts in Firebase.
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
