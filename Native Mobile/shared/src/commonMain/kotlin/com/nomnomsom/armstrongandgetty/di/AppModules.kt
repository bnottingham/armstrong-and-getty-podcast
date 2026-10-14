package com.nomnomsom.armstrongandgetty.di

import androidx.room.RoomDatabase
import com.nomnomsom.armstrongandgetty.data.local.PodcastDatabase
import com.nomnomsom.armstrongandgetty.data.local.UserDeletionTracker
import com.nomnomsom.armstrongandgetty.data.remote.AudioDownloader
import com.nomnomsom.armstrongandgetty.data.remote.RssFeedParser
import com.nomnomsom.armstrongandgetty.data.repository.PodcastRepository
import com.nomnomsom.armstrongandgetty.download.DownloadPolicy
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
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Platform module supplies: RoomDatabase.Builder<PodcastDatabase>, the HttpClient
 * (with a platform engine), the [AudioHttpClient] used only for episode audio,
 * UserDeletionTracker, PlatformPlayer, AnalyticsTracker, ReviewStateStore,
 * ReviewEnvironment, DownloadSettings and NetworkStatus.
 */
expect fun platformModule(): Module

/**
 * HttpClient for episode audio downloads only. Android: the same OkHttp client. iOS: a
 * Darwin client that refuses expensive (cellular/hotspot) routes per request while
 * "Download over Wi-Fi only" is on. The feed keeps the unqualified client, so refresh and
 * notifications work on any network.
 */
val AudioHttpClient = named("audio_http")

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
    single { AudioDownloader(get(AudioHttpClient), get<DownloadPolicy>()) }
    single {
        PodcastRepository(
            dao = get(),
            rssFeedParser = get<RssFeedParser>(),
            audioDownloader = get<AudioDownloader>(),
            deletionTracker = get<UserDeletionTracker>(),
            downloadGate = get<DownloadPolicy>()
        )
    }

    single { PlaybackController(get()) }

    // "Download over Wi-Fi only" gate (setting default off). Lazy, side-effect-free.
    single { DownloadPolicy(get(), get()) }

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

    viewModel { EpisodeListViewModel(get(), get(), get(), get(), get(), get()) }
}
