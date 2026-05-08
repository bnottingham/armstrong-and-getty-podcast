package com.nomnomsom.armstrongandgetty

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.cast.framework.CastContext
import com.nomnomsom.armstrongandgetty.data.repository.PodcastRepository
import com.nomnomsom.armstrongandgetty.work.NewEpisodeCheckWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class AGPodcastApp : Application(), Configuration.Provider {

    companion object {
        private const val TAG = "AGPodcastApp"
    }

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var repository: PodcastRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Initialize CastContext early to avoid UI-thread blocking during first use. Devices
        // without healthy Play Services should still be able to use local playback.
        try {
            CastContext.getSharedInstance(this)
        } catch (e: Exception) {
            Log.w(TAG, "Cast unavailable during app startup", e)
        }

        // Any row stuck at DOWNLOADING here was orphaned by a previous process death — the active
        // downloader is gone but the DB still claims work in flight. Reset before the worker runs
        // so the user sees an actionable retry state.
        appScope.launch { repository.resetStaleDownloadingStates() }

        scheduleNewEpisodeCheck()
    }

    private fun scheduleNewEpisodeCheck() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<NewEpisodeCheckWorker>(
            repeatInterval = 10,
            repeatIntervalTimeUnit = TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            NewEpisodeCheckWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}
