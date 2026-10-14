package com.nomnomsom.armstrongandgetty.background

import com.nomnomsom.armstrongandgetty.analytics.AnalyticsBridgeHolder
import com.nomnomsom.armstrongandgetty.analytics.AnalyticsEvents
import com.nomnomsom.armstrongandgetty.analytics.AnalyticsTracker
import com.nomnomsom.armstrongandgetty.data.model.PodcastDay
import com.nomnomsom.armstrongandgetty.data.repository.PodcastRepository
import com.nomnomsom.armstrongandgetty.di.ensureKoinStarted
import com.nomnomsom.armstrongandgetty.download.AutoDownloadAction
import com.nomnomsom.armstrongandgetty.download.DownloadPolicy
import com.nomnomsom.armstrongandgetty.download.DownloadSettings
import com.nomnomsom.armstrongandgetty.download.WifiRequiredException
import com.nomnomsom.armstrongandgetty.download.autoDownloadCandidates
import com.nomnomsom.armstrongandgetty.download.datesWithNewContent
import com.nomnomsom.armstrongandgetty.download.newlyPublishedDates
import com.nomnomsom.armstrongandgetty.download.planAutoDownloads
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
 *
 * With "Download over Wi-Fi only" on, refresh and notifications still run on any network;
 * downloads off Wi-Fi are held back and caught up by a later run (or app open) on Wi-Fi.
 * With the setting off (the default) the handler behaves exactly as before and never reads
 * the network.
 *
 * Like the worker, a first run (nothing stored yet) takes the feed as a baseline: no
 * notification, and only today's/latest day is downloaded ([newlyPublishedDates]).
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

    // Koin may not be up yet, and the UI may be starting it on the main thread at this same
    // moment (ensureKoinStarted() serializes the two). This runs inside BGTaskScheduler's
    // callback, where an uncaught Kotlin exception aborts the app — fail the task instead.
    val deps = try {
        ensureKoinStarted()
        val koin = KoinPlatform.getKoin()
        RefreshDeps(
            repository = koin.get<PodcastRepository>(),
            analytics = koin.get<AnalyticsTracker>(),
            downloadSettings = koin.get<DownloadSettings>(),
            downloadPolicy = koin.get<DownloadPolicy>(),
        )
    } catch (e: Exception) {
        AppLog.e(TAG, "Background refresh could not start", e)
        AnalyticsBridgeHolder.tracker.recordError("Background refresh could not start", e)
        task.setTaskCompletedWithSuccess(false)
        return
    }

    val (repository, analytics, downloadSettings, downloadPolicy) = deps

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
            val latestDate = refreshResult.getOrThrow()
            val daysAfter = repository.getAllDaysSnapshot()
            val candidates = if (latestDate.isBlank()) emptyList()
                else autoDownloadCandidates(latestDate, repository.todayKey())
            // Days new to the database but not newly published (a first run, or the run after
            // an interrupted first refresh) are neither announced nor downloaded.
            val published = newlyPublishedDates(daysBefore, daysAfter, candidates)
            val wifiOnly = downloadSettings.wifiOnly.value
            // Setting off (the default): always true, and the network is never read.
            val downloadsAllowed = !wifiOnly || downloadPolicy.allowedNow()
            if (daysBefore.isEmpty()) {
                // The listener just installed the app (or it lost its data): nothing is news.
                AppLog.d(TAG, "First run: no notification; auto-download manages $candidates")
            } else {
                notifyIfNewContent(daysBefore, daysAfter, published, waitsForWifi = !downloadsAllowed)
            }

            for (day in daysAfter) {
                if (day.date in published && repository.isUserDeleted(day.date)) {
                    AppLog.d(TAG, "Skipping auto-download for ${day.date} — user previously deleted")
                }
            }

            val plan = planAutoDownloads(
                daysBefore = daysBefore,
                daysAfter = daysAfter,
                candidateDates = candidates,
                // Setting on and downloads allowed now: also catch up today's/latest day an
                // earlier deferral left NONE. Off Wi-Fi those would only be blocked again.
                catchUp = wifiOnly && downloadsAllowed,
                isUserDeleted = repository::isUserDeleted,
                hasAllOnDisk = repository::hasAllSegmentsOnDisk
            )

            // Count a deferral once, when its content is new — not on every run while it waits.
            val newContentDates = datesWithNewContent(daysBefore, daysAfter)
            for (action in plan) {
                val error = when (action) {
                    is AutoDownloadAction.DownloadDay -> {
                        if (action.date in daysBefore) {
                            AppLog.d(TAG, "Auto-downloading deferred episode: ${action.date}")
                        } else {
                            AppLog.d(TAG, "Auto-downloading new episode: ${action.date}")
                        }
                        repository.downloadDay(action.date).exceptionOrNull()
                    }
                    is AutoDownloadAction.AppendSegments -> {
                        AppLog.d(TAG, "Auto-downloading new/missing segments for: ${action.date}")
                        repository.appendNewSegments(action.date).exceptionOrNull()
                    }
                }
                if (error is WifiRequiredException) {
                    AppLog.d(TAG, "Download for ${action.date} waits for Wi-Fi (Wi-Fi only is on)")
                    if (action.date in newContentDates) {
                        analytics.logEvent(
                            AnalyticsEvents.DOWNLOAD_WAITING_WIFI,
                            mapOf(AnalyticsEvents.PARAM_DATE to action.date, AnalyticsEvents.PARAM_SOURCE to "background")
                        )
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

private data class RefreshDeps(
    val repository: PodcastRepository,
    val analytics: AnalyticsTracker,
    val downloadSettings: DownloadSettings,
    val downloadPolicy: DownloadPolicy,
)

/**
 * Mirrors NewEpisodeCheckWorker's notification: diff segment counts before/after refresh.
 * A day new to the database counts only when it is in [published] ([newlyPublishedDates]).
 * [waitsForWifi] = "Download over Wi-Fi only" is on and this isn't Wi-Fi, so a new episode
 * isn't promised as ready.
 */
private fun notifyIfNewContent(
    daysBefore: Map<String, Int>,
    daysAfter: List<PodcastDay>,
    published: Set<String>,
    waitsForWifi: Boolean,
) {
    var newEpisodeFound = false
    var newSegmentsFound = false
    var totalNewSegments = 0

    for (day in daysAfter) {
        val previousCount = daysBefore[day.date]
        if (previousCount == null) {
            if (day.date in published) {
                newEpisodeFound = true
                totalNewSegments += day.segmentCount
            }
        } else if (day.segmentCount > previousCount) {
            newSegmentsFound = true
            totalNewSegments += (day.segmentCount - previousCount)
        }
    }

    if (!newEpisodeFound && !newSegmentsFound) return

    val title = if (newEpisodeFound) "New A&G Episode Available" else "New A&G Segments Available"
    val plural = if (totalNewSegments != 1) "s" else ""
    val body = if (newEpisodeFound && waitsForWifi) {
        "Today's show is out — it'll download when you're on Wi-Fi"
    } else if (newEpisodeFound) {
        "Today's show is ready — $totalNewSegments segment$plural available"
    } else {
        "$totalNewSegments new segment$plural added to today's show"
    }
    AppNotifications.postNewContent(title, body)
}
