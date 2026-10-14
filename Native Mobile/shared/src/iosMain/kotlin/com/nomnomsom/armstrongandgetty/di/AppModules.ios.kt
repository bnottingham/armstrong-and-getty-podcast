package com.nomnomsom.armstrongandgetty.di

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.nomnomsom.armstrongandgetty.analytics.AnalyticsBridgeHolder
import com.nomnomsom.armstrongandgetty.analytics.AnalyticsTracker
import com.nomnomsom.armstrongandgetty.data.local.PodcastDatabase
import com.nomnomsom.armstrongandgetty.data.local.UserDeletionTracker
import com.nomnomsom.armstrongandgetty.download.DownloadSettings
import com.nomnomsom.armstrongandgetty.download.IosNetworkStatus
import com.nomnomsom.armstrongandgetty.download.NetworkStatus
import com.nomnomsom.armstrongandgetty.media.AvPlatformPlayer
import com.nomnomsom.armstrongandgetty.media.CastAwarePlatformPlayer
import com.nomnomsom.armstrongandgetty.media.PlatformPlayer
import com.nomnomsom.armstrongandgetty.review.IosReviewEnvironment
import com.nomnomsom.armstrongandgetty.review.ReviewEnvironment
import com.nomnomsom.armstrongandgetty.review.ReviewStateStore
import com.russhwolf.settings.NSUserDefaultsSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
actual fun platformModule(): Module = module {
    single<RoomDatabase.Builder<PodcastDatabase>> {
        val supportDir = NSSearchPathForDirectoriesInDomains(
            NSApplicationSupportDirectory, NSUserDomainMask, true
        ).firstOrNull() as? String ?: error("No Application Support directory")
        // Unlike Documents, Application Support is NOT auto-created on iOS.
        NSFileManager.defaultManager.createDirectoryAtPath(
            supportDir, withIntermediateDirectories = true, attributes = null, error = null
        )
        Room.databaseBuilder<PodcastDatabase>(
            name = "$supportDir/${PodcastDatabase.DB_NAME}"
        ).setDriver(BundledSQLiteDriver())
    }

    single { HttpClient(Darwin) { httpTimeouts() } }

    single { UserDeletionTracker() }

    single<PlatformPlayer> { CastAwarePlatformPlayer(AvPlatformPlayer()) }

    single<AnalyticsTracker> { AnalyticsBridgeHolder.tracker }

    // In-app review state: new `in_app_review.`-prefixed keys in standard NSUserDefaults.
    single { ReviewStateStore(NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults)) }
    single<ReviewEnvironment> { IosReviewEnvironment() }

    // "Download over Wi-Fi only" (default off): new `download_settings.`-prefixed key in
    // standard NSUserDefaults. The path monitor starts on its first query, not here.
    single { DownloadSettings(NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults)) }
    single<NetworkStatus> { IosNetworkStatus() }
}
