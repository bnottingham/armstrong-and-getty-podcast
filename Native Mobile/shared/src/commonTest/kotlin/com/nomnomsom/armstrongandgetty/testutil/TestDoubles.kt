package com.nomnomsom.armstrongandgetty.testutil

import com.nomnomsom.armstrongandgetty.analytics.AnalyticsTracker
import com.nomnomsom.armstrongandgetty.download.DownloadGate
import com.nomnomsom.armstrongandgetty.media.PlatformPlayer
import com.nomnomsom.armstrongandgetty.media.PlayerItem
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineCapability
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.utils.io.InternalAPI
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

/** Records every analytics event in order. */
class RecordingAnalytics : AnalyticsTracker {
    val events = mutableListOf<Pair<String, Map<String, Any>>>()

    fun named(name: String) = events.filter { it.first == name }.map { it.second }

    override fun logEvent(name: String, params: Map<String, Any>) {
        events += name to params
    }
    override fun logScreen(screenName: String) {}
    override fun setUserProperty(name: String, value: String?) {}
    override fun recordError(message: String, throwable: Throwable?) {}
}

/** Silent player for view-model tests. */
class FakePlatformPlayer : PlatformPlayer {
    override fun connect() {}
    override fun disconnect() {}
    override fun setItems(items: List<PlayerItem>, startWindow: Int, startPositionMs: Long, autoPlay: Boolean) {}
    override fun addItems(items: List<PlayerItem>) {}
    override fun clearItemsAndStop() {}
    override fun play() {}
    override fun pause() {}
    override fun seekTo(windowIndex: Int, positionMs: Long) {}
    override fun setSpeed(speed: Float) {}
    override val itemCount: Int = 0
    override val currentWindowIndex: Int = 0
    override val currentPositionInWindowMs: Long = 0L
    override val isPlaying: Boolean = false
    override val speed: Float = 1f
    override val isReadyOrBuffering: Boolean = false
    override fun setOnStateChanged(callback: (() -> Unit)?) {}
}

/** A [DownloadGate] tests open and close from any thread; counts its checks. */
@OptIn(ExperimentalAtomicApi::class)
class ToggleGate(open: Boolean) : DownloadGate {
    private val isOpen = AtomicBoolean(open)
    private val checkCount = AtomicInt(0)

    var open: Boolean
        get() = isOpen.load()
        set(value) = isOpen.store(value)

    val checks: Int get() = checkCount.load()

    override suspend fun allowedNow(): Boolean {
        checkCount.incrementAndFetch()
        return isOpen.load()
    }
}

/**
 * Minimal Ktor engine whose every request runs [handler] — enough to drive AudioDownloader
 * through failures and hangs without a network. Supports the timeout capability the
 * downloader's per-request `timeout {}` needs.
 */
@OptIn(ExperimentalAtomicApi::class)
class ScriptedEngine(
    private val handler: suspend (HttpRequestData) -> HttpResponseData
) : HttpClientEngineBase("scripted") {
    private val requestCount = AtomicInt(0)
    val requests: Int get() = requestCount.load()

    override val config: HttpClientEngineConfig = HttpClientEngineConfig()
    override val supportedCapabilities: Set<HttpClientEngineCapability<*>> = setOf(HttpTimeoutCapability)

    @InternalAPI
    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        requestCount.incrementAndFetch()
        return handler(data)
    }
}
