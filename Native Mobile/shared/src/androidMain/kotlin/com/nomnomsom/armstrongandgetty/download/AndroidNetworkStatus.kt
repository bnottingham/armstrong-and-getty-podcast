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

    /** No capabilities (no default network) or no INTERNET = offline. */
    private fun kindOf(caps: NetworkCapabilities?): NetworkKind = when {
        caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ->
            NetworkKind.OFFLINE
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) -> NetworkKind.UNMETERED
        else -> NetworkKind.METERED
    }

    override fun current(): NetworkKind = try {
        val cm = connectivity
        if (cm == null) NetworkKind.UNKNOWN else kindOf(cm.activeNetwork?.let { cm.getNetworkCapabilities(it) })
    } catch (e: Exception) {
        AppLog.w(TAG, "Network query failed: ${e.message}")
        NetworkKind.UNKNOWN
    }

    /** ConnectivityManager answers synchronously; there is nothing to wait for. */
    override suspend fun awaitKnown(timeoutMs: Long): NetworkKind = current()

    override fun changes(): Flow<NetworkKind> = callbackFlow {
        trySend(current())
        // Map what each callback is handed; ConnectivityManager queries made inside these
        // callbacks can return stale data (NetworkCallback docs). onAvailable needs nothing:
        // on API 26+ onCapabilitiesChanged always follows it with the new network's caps.
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                trySend(kindOf(networkCapabilities))
            }

            // The default network went away. If another takes over, its own
            // onAvailable/onCapabilitiesChanged follow and replace this reading.
            override fun onLost(network: Network) {
                trySend(NetworkKind.OFFLINE)
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
