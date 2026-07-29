package com.nomnomsom.armstrongandgetty.di

import androidx.room.RoomDatabase
import com.nomnomsom.armstrongandgetty.data.local.PodcastDatabase
import com.nomnomsom.armstrongandgetty.data.remote.AudioDownloader
import com.nomnomsom.armstrongandgetty.data.remote.RssFeedParser
import com.nomnomsom.armstrongandgetty.data.repository.PodcastRepository
import com.nomnomsom.armstrongandgetty.media.PlaybackController
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
 * (with a platform engine), UserDeletionTracker, and PlatformPlayer.
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
    single { PodcastRepository(get(), get(), get(), get()) }

    single { PlaybackController(get()) }

    viewModel { EpisodeListViewModel(get(), get()) }
}
