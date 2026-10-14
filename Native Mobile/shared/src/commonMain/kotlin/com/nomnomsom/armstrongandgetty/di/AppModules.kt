package com.nomnomsom.armstrongandgetty.di

import androidx.room.RoomDatabase
import com.nomnomsom.armstrongandgetty.data.local.PodcastDatabase
import com.nomnomsom.armstrongandgetty.data.local.UserDeletionTracker
import com.nomnomsom.armstrongandgetty.data.remote.AudioDownloader
import com.nomnomsom.armstrongandgetty.data.remote.RssFeedParser
import com.nomnomsom.armstrongandgetty.data.repository.PodcastRepository
import com.nomnomsom.armstrongandgetty.media.PlaybackController
import com.nomnomsom.armstrongandgetty.review.ReviewCoordinator
import com.nomnomsom.armstrongandgetty.review.SessionHealth
import com.nomnomsom.armstrongandgetty.ui.screens.episodelist.EpisodeListViewModel
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Platform module supplies: RoomDatabase.Builder<PodcastDatabase>, the HttpClient
 * (with a platform engine), UserDeletionTracker, PlatformPlayer, AnalyticsTracker,
 * ReviewStateStore, and ReviewEnvironment.
 */
expect fun platformModule(): Module

/** Default timeouts for the app HttpClient; download attempts override per-request. */
val httpTimeouts: HttpClientConfig<*>.() -> Unit = {
    install(HttpTimeout) {
        connectTimeoutMillis = 30_000
        requestTimeoutMillis = 300_000
        socketTimeoutMillis = 120_000
    }
}

val commonModule = module {
    single {
        get<RoomDatabase.Builder<PodcastDatabase>>()
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }
    single { get<PodcastDatabase>().podcastDayDao() }

    single { RssFeedParser(get()) }
    single { AudioDownloader(get()) }
    single {
        PodcastRepository(
            dao = get(),
            rssFeedParser = get<RssFeedParser>(),
            audioDownloader = get<AudioDownloader>(),
            deletionTracker = get<UserDeletionTracker>()
        )
    }

    single { PlaybackController(get()) }

    // In-app rating prompt. Lazy singles, resolved only from App() composition (main thread).
    single { SessionHealth() }
    single {
        ReviewCoordinator(
            store = get(),
            environment = get(),
            playbackState = get<PlaybackController>().playbackState,
            sessionHealth = get(),
            analytics = get(),
        )
    }

    viewModel { EpisodeListViewModel(get(), get(), get(), get()) }
}
