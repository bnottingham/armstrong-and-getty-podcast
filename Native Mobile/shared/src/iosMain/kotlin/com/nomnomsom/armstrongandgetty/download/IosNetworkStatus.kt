package com.nomnomsom.armstrongandgetty.download

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withTimeoutOrNull
import platform.Network.nw_path_get_status
import platform.Network.nw_path_is_expensive
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_monitor_t
import platform.Network.nw_path_status_satisfied
import platform.Network.nw_path_t
import platform.darwin.dispatch_queue_create

/**
 * [NetworkStatus] on Network.framework's `nw_path_monitor`. UNMETERED = the path is
 * satisfied and not expensive ("expensive" covers cellular and Personal Hotspot). Low Data
 * Mode (`nw_path_is_constrained`) is deliberately not treated as metered.
 *
 * The monitor starts lazily on the first query (never in the constructor — Koin startup
 * race) and lives for the process. Until its first update the reading is UNKNOWN.
 */
@OptIn(ExperimentalForeignApi::class)
class IosNetworkStatus : NetworkStatus {

    private val state = MutableStateFlow(NetworkKind.UNKNOWN)

    private val monitor: nw_path_monitor_t by lazy {
        val m = nw_path_monitor_create()
        nw_path_monitor_set_queue(m, dispatch_queue_create("ag.download.network", null))
        nw_path_monitor_set_update_handler(m) { path -> state.value = kindOf(path) }
        nw_path_monitor_start(m)
        m
    }

    private fun kindOf(path: nw_path_t): NetworkKind = when {
        nw_path_get_status(path) != nw_path_status_satisfied -> NetworkKind.OFFLINE
        nw_path_is_expensive(path) -> NetworkKind.METERED
        else -> NetworkKind.UNMETERED
    }

    override fun current(): NetworkKind {
        monitor
        return state.value
    }

    override suspend fun awaitKnown(timeoutMs: Long): NetworkKind {
        monitor
        return withTimeoutOrNull(timeoutMs) { state.first { it != NetworkKind.UNKNOWN } }
            ?: NetworkKind.UNKNOWN
    }

    override fun changes(): Flow<NetworkKind> = state.onStart { monitor }
}
