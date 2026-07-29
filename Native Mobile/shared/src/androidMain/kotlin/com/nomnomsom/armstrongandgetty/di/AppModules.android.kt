package com.nomnomsom.armstrongandgetty.di

import androidx.room.Room
import androidx.room.RoomDatabase
import com.nomnomsom.armstrongandgetty.analytics.AnalyticsTracker
import com.nomnomsom.armstrongandgetty.analytics.FirebaseAnalyticsTracker
import com.nomnomsom.armstrongandgetty.data.local.PodcastDatabase
import com.nomnomsom.armstrongandgetty.data.local.UserDeletionTracker
import com.nomnomsom.armstrongandgetty.media.Media3PlatformPlayer
import com.nomnomsom.armstrongandgetty.media.PlatformPlayer
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
}
