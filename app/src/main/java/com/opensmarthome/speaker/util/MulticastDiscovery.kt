package com.opensmarthome.speaker.util

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Discovered OpenSmartSpeaker instance on the local network. Host + port are
 * populated lazily by NsdManager.resolveService; until resolution succeeds
 * they remain null and only [serviceName] is meaningful.
 */
data class DiscoveredSpeaker(
    val serviceName: String,
    val host: String? = null,
    val port: Int? = null
)

/**
 * mDNS/Network Service Discovery helper for Multi-room. Scans the LAN for
 * `_opensmartspeaker._tcp` services so one tablet can find its siblings and
 * later broadcast timers / announcements to them.
 *
 * Broadcasting / registering our own service is intentionally *not* wired
 * here yet — that requires a listening port and a protocol agreement, both
 * of which belong in a follow-up PR. This skeleton delivers the read side so
 * UI work can start surfacing nearby devices.
 */
@Singleton
class MulticastDiscovery @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val nsdManager by lazy {
        context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    }

    private val _speakers = MutableStateFlow<List<DiscoveredSpeaker>>(emptyList())
    val speakers: StateFlow<List<DiscoveredSpeaker>> = _speakers.asStateFlow()

    private var discoveryListener: NsdManager.DiscoveryListener? = null

    companion object {
        const val SERVICE_TYPE = "_opensmartspeaker._tcp."
    }

    fun start() {
        if (discoveryListener != null) return

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Timber.d("mDNS discovery started for $regType")
            }
            override fun onServiceFound(service: NsdServiceInfo) {
                val next = _speakers.value.toMutableList()
                if (next.none { it.serviceName == service.serviceName }) {
                    next.add(DiscoveredSpeaker(serviceName = service.serviceName))
                    _speakers.value = next.toList()
                }
                resolve(service)
            }
            override fun onServiceLost(service: NsdServiceInfo) {
                _speakers.value = _speakers.value.filterNot { it.serviceName == service.serviceName }
            }
            override fun onDiscoveryStopped(serviceType: String) {
                Timber.d("mDNS discovery stopped")
            }
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Timber.w("mDNS start failed: $errorCode")
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Timber.w("mDNS stop failed: $errorCode")
            }
        }
        discoveryListener = listener
        runCatching {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        }.onFailure { Timber.w(it, "mDNS discoverServices threw") }
    }

    fun stop() {
        discoveryListener?.let {
            runCatching { nsdManager.stopServiceDiscovery(it) }
        }
        discoveryListener = null
    }

    private fun resolve(service: NsdServiceInfo) {
        val resolver = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Timber.d("Resolve failed: ${serviceInfo.serviceName} ($errorCode)")
            }
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val host = serviceInfo.host?.hostAddress
                val port = serviceInfo.port
                _speakers.value = _speakers.value.map {
                    if (it.serviceName == serviceInfo.serviceName) {
                        it.copy(host = host, port = port)
                    } else it
                }
            }
        }
        runCatching { nsdManager.resolveService(service, resolver) }
            .onFailure { Timber.w(it, "mDNS resolve threw") }
    }
}
