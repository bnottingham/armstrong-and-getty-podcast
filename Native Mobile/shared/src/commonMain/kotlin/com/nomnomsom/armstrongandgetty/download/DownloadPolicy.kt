package com.nomnomsom.armstrongandgetty.download

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import okio.IOException

/**
 * Whether an audio transfer may start (or continue) right now. Consulted by
 * `PodcastRepository` before any download state change and by `AudioDownloader` per
 * attempt. Feed refresh, notifications, playback and casting never consult it.
 */
fun interface DownloadGate {
    suspend fun allowedNow(): Boolean

    companion object {
        /** No gate: every download may run (the behaviour before the Wi-Fi-only setting). */
        val Always = DownloadGate { true }
    }
}

/**
 * A download was held back because "Download over Wi-Fi only" is on and the device isn't on
 * an unmetered connection. **Blocked is not failed:** the day stays `NONE`, nothing enters
 * `segmentsFailed`, and no `download_failed` / Crashlytics / SessionHealth failure is recorded.
 */
class WifiRequiredException : IOException("Download over Wi-Fi only is on; not on Wi-Fi")

/**
 * The Wi-Fi-only download gate. With the setting OFF (the default) it allows everything and
 * never reads the network. With it ON, downloads run only on an unmetered connection;
 * metered, offline and unknown all block.
 */
class DownloadPolicy(
    private val settings: DownloadSettings,
    private val network: NetworkStatus,
) : DownloadGate {

    override suspend fun allowedNow(): Boolean = blockedBy() == null

    /**
     * Null when downloads may run now; otherwise the network reading that blocks them
     * (METERED, OFFLINE or UNKNOWN), so a manual tap can explain *why*. With the setting
     * OFF it returns null without consulting the network (the default path).
     */
    suspend fun blockedBy(): NetworkKind? {
        if (!settings.wifiOnly.value) return null // default path: network never consulted
        val reading = network.awaitKnown(NETWORK_WAIT_MS)
        return if (decide(true, reading)) null else reading
    }

    /** True whenever downloads may run. With the setting OFF it never subscribes to the network. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val allowedFlow: Flow<Boolean> = settings.wifiOnly
        .flatMapLatest { on -> if (!on) flowOf(true) else network.changes().map { decide(true, it) } }
        .distinctUntilChanged()

    companion object {
        /** How long a check waits for a first reading (iOS path monitor start-up). */
        const val NETWORK_WAIT_MS = 2_000L

        /**
         * Pure. OFF allows everything. ON = unmetered only; METERED, OFFLINE and UNKNOWN all
         * block (offline blocks so a tunnel between mobile data and Wi-Fi leaves a day `NONE`
         * rather than turning it into a failed download).
         */
        fun decide(wifiOnly: Boolean, network: NetworkKind): Boolean =
            !wifiOnly || network == NetworkKind.UNMETERED
    }
}
