package com.nomnomsom.armstrongandgetty.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nomnomsom.armstrongandgetty.MainActivity
import com.nomnomsom.armstrongandgetty.R
import com.nomnomsom.armstrongandgetty.data.model.DownloadState
import com.nomnomsom.armstrongandgetty.data.model.state
import com.nomnomsom.armstrongandgetty.data.repository.PodcastRepository
import kotlinx.coroutines.CancellationException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Periodic worker: refresh the RSS feed, notify on new episodes/segments, and
 * auto-download any new content.
 */
class NewEpisodeCheckWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams), KoinComponent {

    private val repository: PodcastRepository by inject()

    companion object {
        const val TAG = "NewEpisodeCheck"
        const val WORK_NAME = "new_episode_check"
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

            if (!newEpisodeFound && !newSegmentsFound) {
                Log.d(TAG, "No new content found")
                return Result.success()
            }

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

            for (day in daysAfter) {
                val previousCount = daysBefore[day.date]
                val isNew = previousCount == null
                val hasNewSegments = previousCount != null && day.segmentCount > previousCount

                if (isNew) {
                    if (repository.isUserDeleted(day.date)) {
                        Log.d(TAG, "Skipping auto-download for ${day.date} — user previously deleted")
                    } else {
                        Log.d(TAG, "Auto-downloading new episode: ${day.date}")
                        repository.downloadDay(day.date)
                    }
                } else if (hasNewSegments && day.state == DownloadState.DOWNLOADED) {
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
