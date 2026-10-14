package com.nomnomsom.armstrongandgetty.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.nomnomsom.armstrongandgetty.analytics.AnalyticsTracker
import com.nomnomsom.armstrongandgetty.analytics.FirebaseAnalyticsTracker
import com.nomnomsom.armstrongandgetty.data.local.PodcastDatabase
import com.nomnomsom.armstrongandgetty.data.local.UserDeletionTracker
import com.nomnomsom.armstrongandgetty.download.AndroidNetworkStatus
import com.nomnomsom.armstrongandgetty.download.DownloadSettings
import com.nomnomsom.armstrongandgetty.download.DownloadSettingsKeys
import com.nomnomsom.armstrongandgetty.download.NetworkStatus
import com.nomnomsom.armstrongandgetty.media.Media3PlatformPlayer
import com.nomnomsom.armstrongandgetty.media.PlatformPlayer
import com.nomnomsom.armstrongandgetty.review.AndroidReviewEnvironment
import com.nomnomsom.armstrongandgetty.review.ReviewEnvironment
import com.nomnomsom.armstrongandgetty.review.ReviewKeys
import com.nomnomsom.armstrongandgetty.review.ReviewStateStore
import com.russhwolf.settings.SharedPreferencesSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single<RoomDatabase.Builder<PodcastDatabase>> {
        // Same database file the previous Android-only app used (default databases dir),
        // so existing installs keep episodes and listen positions.
        Room.databaseBuilder<PodcastDatabase>(
            context = androidContext(),
            name = androidContext().getDatabasePath(PodcastDatabase.DB_NAME).absolutePath
        )
    }

    single { HttpClient(OkHttp) { httpTimeouts() } }

    single { UserDeletionTracker(androidContext()) }

    single<PlatformPlayer> { Media3PlatformPlayer(androidContext()) }

    single<AnalyticsTracker> { FirebaseAnalyticsTracker(androidContext()) }

    // In-app review state lives in its OWN prefs file — never "user_deletions".
    single {
        ReviewStateStore(
            SharedPreferencesSettings(
                androidContext().getSharedPreferences(ReviewKeys.ANDROID_PREFS_FILE, Context.MODE_PRIVATE)
            )
        )
    }
    single<ReviewEnvironment> { AndroidReviewEnvironment(androidContext()) }

    // "Download over Wi-Fi only" (default off) — its OWN prefs file, never "user_deletions"
    // or "in_app_review". Lazy: nothing reads the network until the setting is on.
    single {
        DownloadSettings(
            SharedPreferencesSettings(
                androidContext().getSharedPreferences(DownloadSettingsKeys.ANDROID_PREFS_FILE, Context.MODE_PRIVATE)
            )
        )
    }
    single<NetworkStatus> { AndroidNetworkStatus(androidContext()) }
}
