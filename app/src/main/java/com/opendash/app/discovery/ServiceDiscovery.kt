package com.opendash.app.discovery

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

data class DiscoveredService(
    val name: String,
    val type: String,
    val host: String,
    val port: Int
)

@Singleton
class ServiceDiscovery @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val nsdManager by lazy { context.getSystemService(Context.NSD_SERVICE) as NsdManager }

    private val _discoveredHa = MutableStateFlow<List<DiscoveredService>>(emptyList())
    val discoveredHa: StateFlow<List<DiscoveredService>> = _discoveredHa.asStateFlow()

    private val _discoveredOpenClaw = MutableStateFlow<List<DiscoveredService>>(emptyList())
    val discoveredOpenClaw: StateFlow<List<DiscoveredService>> = _discoveredOpenClaw.asStateFlow()

    private var haListener: NsdManager.DiscoveryListener? = null
    private var openClawListener: NsdManager.DiscoveryListener? = null

    fun startDiscovery() {
        discoverService("_home-assistant._tcp.", _discoveredHa) { haListener = it }
        discoverService("_openclaw._tcp.", _discoveredOpenClaw) { openClawListener = it }
    }

    fun stopDiscovery() {
        haListener?.let { safeStopDiscovery(it) }
        openClawListener?.let { safeStopDiscovery(it) }
        haListener = null
        openClawListener = null
    }

    private fun discoverService(
        serviceType: String,
        target: MutableStateFlow<List<DiscoveredService>>,
        storeListener: (NsdManager.DiscoveryListener) -> Unit
    ) {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) {
                Timber.d("mDNS discovery started for $type")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Timber.d("mDNS service found: ${serviceInfo.serviceName}")
                nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                        Timber.w("mDNS resolve failed: $errorCode")
                    }

                    override fun onServiceResolved(info: NsdServiceInfo) {
                        val service = DiscoveredService(
                            name = info.serviceName,
                            type = info.serviceType,
                            host = info.host?.hostAddress ?: "",
                            port = info.port
                        )
                        val current = target.value.toMutableList()
                        if (current.none { it.host == service.host && it.port == service.port }) {
                            current.add(service)
                            target.value = current
                        }
                        Timber.d("mDNS resolved: ${service.host}:${service.port}")
                    }
                })
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                target.value = target.value.filter { it.name != serviceInfo.serviceName }
            }

            override fun onDiscoveryStopped(type: String) {
                Timber.d("mDNS discovery stopped for $type")
            }

            override fun onStartDiscoveryFailed(type: String, errorCode: Int) {
                Timber.e("mDNS start discovery failed: $errorCode")
            }

            override fun onStopDiscoveryFailed(type: String, errorCode: Int) {
                Timber.e("mDNS stop discovery failed: $errorCode")
            }
        }

        storeListener(listener)
        nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    private fun safeStopDiscovery(listener: NsdManager.DiscoveryListener) {
        try {
            nsdManager.stopServiceDiscovery(listener)
        } catch (e: Exception) {
            Timber.w(e, "Error stopping mDNS discovery")
        }
    }
}
