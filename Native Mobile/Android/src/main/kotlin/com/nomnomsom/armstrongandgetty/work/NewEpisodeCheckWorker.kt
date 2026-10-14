package com.nomnomsom.armstrongandgetty.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nomnomsom.armstrongandgetty.MainActivity
import com.nomnomsom.armstrongandgetty.R
import com.nomnomsom.armstrongandgetty.analytics.AnalyticsEvents
import com.nomnomsom.armstrongandgetty.analytics.AnalyticsTracker
import com.nomnomsom.armstrongandgetty.data.repository.PodcastRepository
import com.nomnomsom.armstrongandgetty.download.AutoDownloadAction
import com.nomnomsom.armstrongandgetty.download.DownloadSettings
import com.nomnomsom.armstrongandgetty.download.WifiRequiredException
import com.nomnomsom.armstrongandgetty.download.autoDownloadCandidates
import com.nomnomsom.armstrongandgetty.download.planAutoDownloads
import kotlinx.coroutines.CancellationException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Periodic worker: refresh the RSS feed, notify on new episodes/segments, and
 * auto-download any new content.
 *
 * The periodic job ([WORK_NAME]) stays `CONNECTED`: refresh and notifications run on any
 * network. With "Download over Wi-Fi only" on, downloads are held back off Wi-Fi and a
 * one-time [WIFI_WORK_NAME] run (same worker, `UNMETERED`) catches them up as soon as Wi-Fi
 * is back. With the setting off (the default) the worker behaves exactly as before.
 */
class NewEpisodeCheckWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams), KoinComponent {

    private val repository: PodcastRepository by inject()
    private val downloadSettings: DownloadSettings by inject()
    private val analytics: AnalyticsTracker by inject()

    companion object {
        const val TAG = "NewEpisodeCheck"
        const val WORK_NAME = "new_episode_check"

        /** One-time catch-up run on the next unmetered connection after a Wi-Fi-only deferral. */
        const val WIFI_WORK_NAME = "new_episode_check_wifi"
        const val CHANNEL_ID = "new_episodes"
        const val NOTIFICATION_ID = 1001
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Checking for new episodes…")

        try {
            // Snapshot segment counts before refresh so we can diff afterward.
            val daysBefore = mutableMapOf<String, Int>()
            val knownDays = repository.getAllDaysSnapshot()
            for (day in knownDays) {
                daysBefore[day.date] = day.segmentCount
            }

            val refreshResult = repository.refreshFeed()
            if (refreshResult.isFailure) {
                Log.w(TAG, "Feed refresh failed: ${refreshResult.exceptionOrNull()?.message}")
                return Result.retry()
            }

            val latestDate = refreshResult.getOrThrow()
            if (latestDate.isBlank()) return Result.success()

            val daysAfter = repository.getAllDaysSnapshot()
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

            val hasNewContent = newEpisodeFound || newSegmentsFound
            // With Wi-Fi only on, a run with nothing new still plans: on Wi-Fi it catches up
            // downloads an earlier run deferred. Off (the default): unchanged early return.
            val wifiOnly = downloadSettings.wifiOnly.value
            if (!hasNewContent && !wifiOnly) {
                Log.d(TAG, "No new content found")
                return Result.success()
            }

            if (hasNewContent) {
                Log.d(TAG, "New content found! newEpisode=$newEpisodeFound newSegments=$newSegmentsFound count=$totalNewSegments")

                val notificationTitle = if (newEpisodeFound) {
                    "New A&G Episode Available"
                } else {
                    "New A&G Segments Available"
                }
                val notificationBody = if (newEpisodeFound) {
                    "Today's show is ready — $totalNewSegments segment${if (totalNewSegments != 1) "s" else ""} available"
                } else {
                    "$totalNewSegments new segment${if (totalNewSegments != 1) "s" else ""} added to today's show"
                }

                showNotification(notificationTitle, notificationBody)
            } else {
                Log.d(TAG, "No new content found; checking for downloads deferred by Wi-Fi only")
            }

            for (day in daysAfter) {
                if (daysBefore[day.date] == null && repository.isUserDeleted(day.date)) {
                    Log.d(TAG, "Skipping auto-download for ${day.date} — user previously deleted")
                }
            }

            val plan = planAutoDownloads(
                daysBefore = daysBefore,
                daysAfter = daysAfter,
                candidateDates = autoDownloadCandidates(latestDate, repository.todayKey()),
                catchUp = wifiOnly,
                isUserDeleted = repository::isUserDeleted,
                hasAllOnDisk = repository::hasAllSegmentsOnDisk
            )

            val deferredDates = mutableListOf<String>()
            for (action in plan) {
                val error = when (action) {
                    is AutoDownloadAction.DownloadDay -> {
                        if (action.date in daysBefore) {
                            Log.d(TAG, "Auto-downloading deferred episode: ${action.date}")
                        } else {
                            Log.d(TAG, "Auto-downloading new episode: ${action.date}")
                        }
                        repository.downloadDay(action.date).exceptionOrNull()
                    }
                    is AutoDownloadAction.AppendSegments -> {
                        // Missing files without count growth = an earlier append failed or was
                        // interrupted; appendNewSegments re-fetches whatever isn't on disk.
                        Log.d(TAG, "Auto-downloading new/missing segments for: ${action.date}")
                        repository.appendNewSegments(action.date).exceptionOrNull()
                    }
                }
                if (error is WifiRequiredException) deferredDates += action.date
            }

            if (deferredDates.isNotEmpty()) {
                Log.d(TAG, "Downloads wait for Wi-Fi (Wi-Fi only is on): $deferredDates")
                deferredDates.forEach { date ->
                    analytics.logEvent(
                        AnalyticsEvents.DOWNLOAD_WAITING_WIFI,
                        mapOf(AnalyticsEvents.PARAM_DATE to date, AnalyticsEvents.PARAM_SOURCE to "background")
                    )
                }
                scheduleWifiCatchUp()
            }

            return Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Worker failed", e)
            return Result.retry()
        }
    }

    /**
     * Runs this worker once more as soon as an unmetered network is available, instead of
     * waiting up to 15 min for the periodic job. KEEP: one pending catch-up at most. Concurrent
     * runs are serialized per day by the repository's download lock.
     */
    private fun scheduleWifiCatchUp() {
        try {
            WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                WIFI_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<NewEpisodeCheckWorker>()
                    .setConstraints(
                        Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build()
                    )
                    .build()
            )
        } catch (e: Exception) {
            // The periodic run (or the next app open) catches up instead.
            Log.w(TAG, "Could not schedule the Wi-Fi catch-up run", e)
        }
    }

    private fun showNotification(title: String, body: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            CHANNEL_ID,
            "New Episodes",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications when new Armstrong & Getty episodes are available"
        }
        notificationManager.createNotificationChannel(channel)

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
