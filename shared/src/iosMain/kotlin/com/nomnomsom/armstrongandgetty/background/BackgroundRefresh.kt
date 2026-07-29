package com.nomnomsom.armstrongandgetty.background

import com.nomnomsom.armstrongandgetty.analytics.AnalyticsEvents
import com.nomnomsom.armstrongandgetty.analytics.AnalyticsTracker
import com.nomnomsom.armstrongandgetty.data.model.DownloadState
import com.nomnomsom.armstrongandgetty.data.model.PodcastDay
import com.nomnomsom.armstrongandgetty.data.model.state
import com.nomnomsom.armstrongandgetty.data.repository.PodcastRepository
import com.nomnomsom.armstrongandgetty.di.ensureKoinStarted
import com.nomnomsom.armstrongandgetty.notifications.AppNotifications
import com.nomnomsom.armstrongandgetty.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform
import platform.BackgroundTasks.BGAppRefreshTask
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.UIKit.UIApplicationDidEnterBackgroundNotification

/**
 * iOS counterpart of Android's NewEpisodeCheckWorker, on BGTaskScheduler.
 *
 * BGAppRefreshTask is best-effort: iOS decides when (and whether) to run it based on
 * usage patterns — unlike WorkManager there is no guaranteed cadence. The handler
 * mirrors the worker: refresh the feed, auto-download brand-new days (unless the user
 * deleted them), and append new segments to already-downloaded days.
 */
private const val TAG = "BackgroundRefresh"
private const val TASK_ID = "nomnomsom.armstrongandgetty.refresh"
private const val EARLIEST_DELAY_SECONDS = 15.0 * 60

private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/** Must be called before the app finishes launching (the Swift App struct's init). */
fun registerBackgroundRefresh() {
    BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(
        identifier = TASK_ID,
        usingQueue = null
    ) { task ->
        handleRefresh(task as BGAppRefreshTask)
    }

    // Re-arm whenever the app moves to the background.
    NSNotificationCenter.defaultCenter.addObserverForName(
        name = UIApplicationDidEnterBackgroundNotification,
        `object` = null,
        queue = NSOperationQueue.mainQueue
    ) { _ ->
        scheduleNextRefresh()
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun scheduleNextRefresh() {
    val request = BGAppRefreshTaskRequest(identifier = TASK_ID)
    request.earliestBeginDate = NSDate.dateWithTimeIntervalSinceNow(EARLIEST_DELAY_SECONDS)
    val submitted = BGTaskScheduler.sharedScheduler.submitTaskRequest(request, error = null)
    AppLog.d(TAG, "scheduleNextRefresh submitted=$submitted")
}

private fun handleRefresh(task: BGAppRefreshTask) {
    scheduleNextRefresh()

    // A background launch never builds the UI, so Koin may not be up yet.
    ensureKoinStarted()
    val repository = KoinPlatform.getKoin().get<PodcastRepository>()
    val analytics = KoinPlatform.getKoin().get<AnalyticsTracker>()

    val job = scope.launch {
        try {
            val daysBefore = repository.getAllDaysSnapshot().associate { it.date to it.segmentCount }

            val refreshResult = repository.refreshFeed()
            if (refreshResult.isFailure) {
                AppLog.w(TAG, "Feed refresh failed: ${refreshResult.exceptionOrNull()?.message}")
                analytics.logEvent(
                    AnalyticsEvents.BACKGROUND_REFRESH,
                    mapOf(AnalyticsEvents.PARAM_RESULT to "failed")
                )
                task.setTaskCompletedWithSuccess(false)
                return@launch
            }

            analytics.logEvent(
                AnalyticsEvents.BACKGROUND_REFRESH,
                mapOf(AnalyticsEvents.PARAM_RESULT to "success")
            )
            notifyIfNewContent(daysBefore, repository.getAllDaysSnapshot())

            for (day in repository.getAllDaysSnapshot()) {
                val previousCount = daysBefore[day.date]
                when {
                    previousCount == null -> {
                        if (repository.isUserDeleted(day.date)) {
                            AppLog.d(TAG, "Skipping auto-download for ${day.date} — user previously deleted")
                        } else {
                            AppLog.d(TAG, "Auto-downloading new episode: ${day.date}")
                            repository.downloadDay(day.date)
                        }
                    }
                    day.segmentCount > previousCount && day.state == DownloadState.DOWNLOADED -> {
                        AppLog.d(TAG, "Auto-downloading new segments for: ${day.date}")
                        repository.appendNewSegments(day.date)
                    }
                }
            }

            task.setTaskCompletedWithSuccess(true)
        } catch (e: Exception) {
            AppLog.e(TAG, "Background refresh failed", e)
            task.setTaskCompletedWithSuccess(false)
        }
    }

    task.expirationHandler = {
        AppLog.w(TAG, "Background refresh expired — cancelling")
        job.cancel()
    }
}

/** Mirrors NewEpisodeCheckWorker's notification: diff segment counts before/after refresh. */
private fun notifyIfNewContent(daysBefore: Map<String, Int>, daysAfter: List<PodcastDay>) {
    var newEpisodeFound = false
    var newSegmentsFound = false
    var totalNewSegments = 0

    for (day in daysAfter) {
        val previousCount = daysBefore[day.date]
        if (previousCount == null) {
            newEpisodeFound = true
            totalNewSegments += day.segmentCount
        } else if (day.segmentCount > previousCount) {
            newSegmentsFound = true
            totalNewSegments += (day.segmentCount - previousCount)
        }
    }

    if (!newEpisodeFound && !newSegmentsFound) return

    val title = if (newEpisodeFound) "New A&G Episode Available" else "New A&G Segments Available"
    val plural = if (totalNewSegments != 1) "s" else ""
    val body = if (newEpisodeFound) {
        "Today's show is ready — $totalNewSegments segment$plural available"
    } else {
        "$totalNewSegments new segment$plural added to today's show"
    }
    AppNotifications.postNewContent(title, body)
}
