package com.nomnomsom.armstrongandgetty.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nomnomsom.armstrongandgetty.MainActivity
import com.nomnomsom.armstrongandgetty.R
import com.nomnomsom.armstrongandgetty.data.model.DownloadState
import com.nomnomsom.armstrongandgetty.data.repository.PodcastRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/**
 * Periodic background worker that:
 * 1. Checks the RSS feed for new episodes/segments every 10 minutes
 * 2. Shows a notification when new content is found
 * 3. Automatically downloads new segments in the background
 *
 * No auth required — available to all users.
 */
@HiltWorker
class NewEpisodeCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: PodcastRepository
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "NewEpisodeCheck"
        const val WORK_NAME = "new_episode_check"
        const val CHANNEL_ID = "new_episodes"
        const val NOTIFICATION_ID = 1001
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Checking for new episodes…")

        try {
            // Snapshot which days we know about and their segment counts before refresh
            val daysBefore = mutableMapOf<String, Int>()
            // We can only get current state via the repo — pull all known days
            val knownDays = repository.getAllDaysSnapshot()
            for (day in knownDays) {
                daysBefore[day.date] = day.segmentCount
            }

            // Refresh the RSS feed — this updates the DB with any new days/segments
            val refreshResult = repository.refreshFeed()
            if (refreshResult.isFailure) {
                Log.w(TAG, "Feed refresh failed: ${refreshResult.exceptionOrNull()?.message}")
                return Result.retry()
            }

            val latestDate = refreshResult.getOrThrow()
            if (latestDate.isBlank()) return Result.success()

            // Check what changed
            val daysAfter = repository.getAllDaysSnapshot()
            var newEpisodeFound = false
            var newSegmentsFound = false
            var newDayDate: String? = null
            var totalNewSegments = 0

            for (day in daysAfter) {
                val previousCount = daysBefore[day.date]

                if (previousCount == null) {
                    // Entirely new day
                    newEpisodeFound = true
                    newDayDate = day.date
                    totalNewSegments += day.segmentCount
                } else if (day.segmentCount > previousCount) {
                    // Existing day got new segments
                    newSegmentsFound = true
                    newDayDate = day.date
                    totalNewSegments += (day.segmentCount - previousCount)
                }
            }

            if (!newEpisodeFound && !newSegmentsFound) {
                Log.d(TAG, "No new content found")
                return Result.success()
            }

            Log.d(TAG, "New content found! newEpisode=$newEpisodeFound newSegments=$newSegmentsFound count=$totalNewSegments")

            // Show notification
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

            // Auto-download new/updated days
            for (day in daysAfter) {
                val previousCount = daysBefore[day.date]
                val isNew = previousCount == null
                val hasNewSegments = previousCount != null && day.segmentCount > previousCount

                if (isNew) {
                    // New day — download everything
                    Log.d(TAG, "Auto-downloading new episode: ${day.date}")
                    repository.downloadDay(day.date)
                } else if (hasNewSegments && day.downloadState == DownloadState.DOWNLOADED.value) {
                    // Existing downloaded day got more segments — append them
                    Log.d(TAG, "Auto-downloading new segments for: ${day.date}")
                    repository.appendNewSegments(day.date)
                }
            }

            return Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Worker failed", e)
            return Result.retry()
        }
    }

    private fun showNotification(title: String, body: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create channel (required for API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "New Episodes",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications when new Armstrong & Getty episodes are available"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Tap notification → open app
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
