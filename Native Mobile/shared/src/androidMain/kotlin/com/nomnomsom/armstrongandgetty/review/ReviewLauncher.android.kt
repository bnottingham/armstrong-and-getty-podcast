package com.nomnomsom.armstrongandgetty.review

import android.app.Activity
import android.os.SystemClock
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.tasks.Task
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import com.nomnomsom.armstrongandgetty.util.AppLog
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "InAppReview"

/** A prefetched ReviewInfo is only valid for a limited time; never reuse one older than this. */
private const val REVIEW_INFO_MAX_AGE_MS = 60_000L

@Composable
actual fun rememberReviewLauncher(): ReviewLauncher {
    val activity = LocalActivity.current
    return remember(activity) { activity?.let(::PlayReviewLauncher) ?: NoActivityLauncher }
}

private object NoActivityLauncher : ReviewLauncher {
    override fun canPresent(): Boolean = false
    override suspend fun launch(): ReviewLaunchResult = ReviewLaunchResult.CONTEXT_GONE
}

/**
 * Google Play In-App Review. Play decides whether a card actually appears (quota, account,
 * install source) and never says, so CALLED only means the flow was launched.
 */
private class PlayReviewLauncher(private val activity: Activity) : ReviewLauncher {
    private val manager: ReviewManager by lazy { ReviewManagerFactory.create(activity.applicationContext) }
    private var prefetched: ReviewInfo? = null
    private var prefetchedAt = 0L

    override fun canPresent(): Boolean = activity.canPresentReview()

    override suspend fun prepare() {
        if (freshPrefetch() != null) return
        try {
            prefetched = manager.requestReviewFlow().await()
            prefetchedAt = SystemClock.elapsedRealtime()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // launch() asks again and reports UNAVAILABLE if it still fails.
            AppLog.w(TAG, "Play review prefetch failed", e)
        }
    }

    override suspend fun launch(): ReviewLaunchResult = try {
        val info = freshPrefetch() ?: manager.requestReviewFlow().await()
        prefetched = null
        when {
            info == null -> ReviewLaunchResult.UNAVAILABLE
            // Re-checked after the (possibly async) fetch: never launch over a paused or
            // unfocused Activity.
            !activity.canPresentReview() -> ReviewLaunchResult.CONTEXT_GONE
            else -> {
                manager.launchReviewFlow(activity, info).await()
                ReviewLaunchResult.CALLED
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // ReviewException (PLAY_STORE_NOT_FOUND, INVALID_REQUEST, INTERNAL_ERROR, …) included.
        AppLog.w(TAG, "Play in-app review unavailable", e)
        ReviewLaunchResult.UNAVAILABLE
    }

    private fun freshPrefetch(): ReviewInfo? =
        prefetched?.takeIf { SystemClock.elapsedRealtime() - prefetchedAt <= REVIEW_INFO_MAX_AGE_MS }
}

/** Resumed, not finishing/destroyed, and its window has focus (no shade, dialog or chooser on top). */
private fun Activity.canPresentReview(): Boolean {
    val resumed = (this as? LifecycleOwner)?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true
    return resumed && !isFinishing && !isDestroyed && hasWindowFocus()
}

/** Minimal Task → coroutine bridge (avoids review-ktx and its old fragment/coroutines deps). */
private suspend fun <T> Task<T>.await(): T? = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        if (task.isSuccessful) {
            cont.resume(task.result)
        } else {
            cont.resumeWithException(task.exception ?: IllegalStateException("Play review task failed"))
        }
    }
}
