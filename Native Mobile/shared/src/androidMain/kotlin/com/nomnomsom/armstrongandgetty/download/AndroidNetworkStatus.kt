package com.nomnomsom.armstrongandgetty.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.nomnomsom.armstrongandgetty.util.AppLog
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * [NetworkStatus] on ConnectivityManager. UNMETERED = the default network has
 * `NET_CAPABILITY_NOT_METERED` (Wi-Fi/Ethernet the user hasn't marked metered).
 * The constructor only stores the context; nothing registers until [changes] is collected.
 */
class AndroidNetworkStatus(private val context: Context) : NetworkStatus {

    private companion object {
        const val TAG = "NetworkStatus"
    }

    private val connectivity: ConnectivityManager?
        get() = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    override fun current(): NetworkKind = try {
        val cm = connectivity
        val caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        when {
            cm == null -> NetworkKind.UNKNOWN
            caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ->
                NetworkKind.OFFLINE
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) -> NetworkKind.UNMETERED
            else -> NetworkKind.METERED
        }
    } catch (e: Exception) {
        AppLog.w(TAG, "Network query failed: ${e.message}")
        NetworkKind.UNKNOWN
    }

    /** ConnectivityManager answers synchronously; there is nothing to wait for. */
    override suspend fun awaitKnown(timeoutMs: Long): NetworkKind = current()

    override fun changes(): Flow<NetworkKind> = callbackFlow {
        trySend(current())
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(current())
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                trySend(current())
            }

            override fun onLost(network: Network) {
                trySend(current())
            }
        }
        val cm = connectivity
        val registered = try {
            cm?.registerDefaultNetworkCallback(callback)
            cm != null
        } catch (e: Exception) {
            AppLog.w(TAG, "Network callback registration failed: ${e.message}")
            false
        }
        if (!registered) trySend(NetworkKind.UNKNOWN)
        awaitClose {
            if (registered) {
                try {
                    cm?.unregisterNetworkCallback(callback)
                } catch (_: Exception) {
                    // Already unregistered or the service is gone; nothing to clean up.
                }
            }
        }
    }.conflate().distinctUntilChanged()
}
