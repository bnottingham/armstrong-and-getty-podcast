package com.nomnomsom.armstrongandgetty

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.cast.framework.CastContext
import com.nomnomsom.armstrongandgetty.work.NewEpisodeCheckWorker
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class AGPodcastApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        
        // Initialize CastContext early to avoid UI-thread blocking during first use
        CastContext.getSharedInstance(this)

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
