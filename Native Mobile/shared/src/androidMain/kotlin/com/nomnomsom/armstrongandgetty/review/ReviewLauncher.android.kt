package com.nomnomsom.armstrongandgetty.review

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.tasks.Task
import com.google.android.play.core.review.ReviewManagerFactory
import com.nomnomsom.armstrongandgetty.util.AppLog
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "InAppReview"

@Composable
actual fun rememberReviewLauncher(): ReviewLauncher {
    val activity = LocalActivity.current
    return remember(activity) {
        ReviewLauncher { activity?.let { launchPlayReview(it) } ?: ReviewLaunchResult.CONTEXT_GONE }
    }
}

/**
 * Google Play In-App Review. Play decides whether a card actually appears (quota, account,
 * install source) and never says, so CALLED only means the flow was launched.
 */
private suspend fun launchPlayReview(activity: Activity): ReviewLaunchResult = try {
    val manager = ReviewManagerFactory.create(activity.applicationContext)
    val info = manager.requestReviewFlow().await()
    val resumed = (activity as? LifecycleOwner)?.lifecycle?.currentState
        ?.isAtLeast(Lifecycle.State.RESUMED) == true
    when {
        info == null -> ReviewLaunchResult.UNAVAILABLE
        activity.isFinishing || activity.isDestroyed || !resumed -> ReviewLaunchResult.CONTEXT_GONE
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
