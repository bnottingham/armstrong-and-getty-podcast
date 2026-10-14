package com.nomnomsom.armstrongandgetty.download

import kotlinx.coroutines.flow.Flow

/** What kind of connection the device has right now, as far as downloads are concerned. */
enum class NetworkKind {
    /** Wi-Fi or Ethernet that isn't metered: downloads may run with "Wi-Fi only" on. */
    UNMETERED,

    /** Mobile data, a hotspot, or a network the user marked as metered. */
    METERED,

    /** No usable connection. */
    OFFLINE,

    /** Not known yet (iOS before the first path update) or the platform query failed. */
    UNKNOWN,
}

/**
 * Platform network reading for the Wi-Fi-only download gate. Only [DownloadPolicy] consults
 * it, and only while the setting is ON — with the setting off nothing here is ever called.
 *
 * Implementations: `AndroidNetworkStatus` (ConnectivityManager) and `IosNetworkStatus`
 * (`nw_path_monitor`). Constructors must have no side effects (Koin startup race).
 */
interface NetworkStatus {
    /** Best reading now; may be [NetworkKind.UNKNOWN] briefly on iOS. */
    fun current(): NetworkKind

    /** Waits up to [timeoutMs] for a non-UNKNOWN reading; UNKNOWN if none arrives. */
    suspend fun awaitKnown(timeoutMs: Long): NetworkKind

    /** Cold flow of readings, starting with the current one. Android registers a callback only while collected. */
    fun changes(): Flow<NetworkKind>
}
