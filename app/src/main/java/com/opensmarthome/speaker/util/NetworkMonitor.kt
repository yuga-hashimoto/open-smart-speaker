package com.opensmarthome.speaker.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks whether the device currently has internet connectivity. Used by
 * the router to skip remote providers when offline (avoids the "network
 * hiccup" error spiral when WiFi is genuinely down).
 */
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val cm by lazy {
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val isOnline: StateFlow<Boolean> = callbackFlow {
        trySend(currentlyOnline())
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(currentlyOnline())
            }
            override fun onLost(network: Network) {
                trySend(currentlyOnline())
            }
            override fun onCapabilitiesChanged(network: Network, cap: NetworkCapabilities) {
                trySend(currentlyOnline())
            }
        }
        try {
            cm.registerDefaultNetworkCallback(callback)
        } catch (_: Exception) {
            // No network permission or device without connectivity manager — assume online.
            trySend(true)
        }
        awaitClose {
            try { cm.unregisterNetworkCallback(callback) } catch (_: Exception) {}
        }
    }.stateIn(scope, SharingStarted.Eagerly, true)

    private fun currentlyOnline(): Boolean {
        val active = try { cm.activeNetwork } catch (_: Exception) { null } ?: return false
        val caps = try { cm.getNetworkCapabilities(active) } catch (_: Exception) { null } ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
