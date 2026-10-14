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
import com.nomnomsom.armstrongandgetty.download.DownloadPolicy
import com.nomnomsom.armstrongandgetty.download.DownloadSettings
import com.nomnomsom.armstrongandgetty.download.WifiRequiredException
import com.nomnomsom.armstrongandgetty.download.autoDownloadCandidates
import com.nomnomsom.armstrongandgetty.download.datesWithNewContent
import com.nomnomsom.armstrongandgetty.download.newlyPublishedDates
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
 * is back. A run with nothing new plans catch-up only while downloads are allowed (on Wi-Fi),
 * and only for today's/latest day. With the setting off (the default) the worker behaves
 * exactly as before and never reads the network.
 *
 * A first run (nothing stored yet: fresh install or cleared data) races the app's own first
 * refresh and takes the feed as a baseline: no notification, and only today's/latest day is
 * downloaded ([newlyPublishedDates]) — never the two years of show days in the feed.
 */
class NewEpisodeCheckWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams), KoinComponent {

    private val repository: PodcastRepository by inject()
    private val downloadSettings: DownloadSettings by inject()
    private val downloadPolicy: DownloadPolicy by inject()
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
            val candidates = autoDownloadCandidates(latestDate, repository.todayKey())
            val firstRun = daysBefore.isEmpty()
            // Days new to the database but not newly published (a first run, or the run after
            // an interrupted first refresh) are neither announced nor downloaded.
            val published = newlyPublishedDates(daysBefore, daysAfter, candidates)
            val baselineDays = daysAfter.count { it.date !in daysBefore && it.date !in published }
            if (baselineDays > 0) {
                Log.d(TAG, "Feed baseline: $baselineDays stored days aren't new episodes (firstRun=$firstRun)")
            }
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

            val hasNewContent = newEpisodeFound || newSegmentsFound
            val wifiOnly = downloadSettings.wifiOnly.value
            // Setting off (the default): always true, and the network is never read.
            val downloadsAllowed = !wifiOnly || downloadPolicy.allowedNow()
            // Catch-up of downloads an earlier run held back: setting on AND allowed right now.
            // Off Wi-Fi it would only be blocked again (after a feed fetch per partial day), so
            // such a run keeps the plain early return below.
            val catchUp = wifiOnly && downloadsAllowed
            if (!hasNewContent && !catchUp) {
                Log.d(TAG, "No new content found")
                return Result.success()
            }

            if (hasNewContent && firstRun) {
                // The listener just installed the app (or cleared its data): nothing is news.
                Log.d(TAG, "First run: no notification; auto-download manages $candidates")
            } else if (hasNewContent) {
                Log.d(TAG, "New content found! newEpisode=$newEpisodeFound newSegments=$newSegmentsFound count=$totalNewSegments")

                val notificationTitle = if (newEpisodeFound) {
                    "New A&G Episode Available"
                } else {
                    "New A&G Segments Available"
                }
                val notificationBody = if (newEpisodeFound && !downloadsAllowed) {
                    // Wi-Fi only is on and this isn't Wi-Fi: don't promise a ready episode.
                    "Today's show is out — it'll download when you're on Wi-Fi"
                } else if (newEpisodeFound) {
                    "Today's show is ready — $totalNewSegments segment${if (totalNewSegments != 1) "s" else ""} available"
                } else {
                    "$totalNewSegments new segment${if (totalNewSegments != 1) "s" else ""} added to today's show"
                }

                showNotification(notificationTitle, notificationBody)
            } else {
                Log.d(TAG, "No new content found; checking for downloads deferred by Wi-Fi only")
            }

            for (day in daysAfter) {
                if (day.date in published && repository.isUserDeleted(day.date)) {
                    Log.d(TAG, "Skipping auto-download for ${day.date} — user previously deleted")
                }
            }

            val fullPlan = planAutoDownloads(
                daysBefore = daysBefore,
                daysAfter = daysAfter,
                candidateDates = candidates,
                catchUp = catchUp,
                isUserDeleted = repository::isUserDeleted,
                hasAllOnDisk = repository::hasAllSegmentsOnDisk
            )
            // A catch-up-only run (nothing new; setting on, on Wi-Fi) manages today's/latest day
            // only, like the app-open pass — an old day's permanently failing hour must not be
            // retried every 15 minutes. With new content (always, with the setting off) the
            // plan is unchanged.
            val plan = if (hasNewContent) fullPlan else fullPlan.filter { it.date in candidates }

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
                // Count a deferral once, when its content is new — not again on every run
                // while it keeps waiting.
                val newContentDates = datesWithNewContent(daysBefore, daysAfter)
                deferredDates.filter { it in newContentDates }.forEach { date ->
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
