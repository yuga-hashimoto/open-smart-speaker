package com.opendash.app.util

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Discovered OpenDash instance on the local network. Host + port are
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
 * `_opendash._tcp` services so one tablet can find its siblings and
 * later broadcast timers / announcements to them.
 *
 * Registration: [register] advertises THIS device on the LAN as
 * `_opendash._tcp.` on [DEFAULT_PORT] so peers can discover us. The
 * registration is an intent signal; there is no RPC server listening on that
 * port yet. The protocol handshake (timers, announcements, message bus) lands
 * in a follow-up PR. Callers must opt in explicitly — the app does not call
 * [register] implicitly. A Settings toggle will drive this in a later PR.
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

    private val _registeredName = MutableStateFlow<String?>(null)
    /**
     * Emits the service name we are currently advertising (after the system
     * confirms registration; may be differentiated from the requested name by
     * NsdManager if a conflict was resolved), or null when we are not
     * broadcasting. UI can show "broadcasting as X" or hide the indicator.
     */
    val registeredName: StateFlow<String?> = _registeredName.asStateFlow()

    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var registrationListener: NsdManager.RegistrationListener? = null

    companion object {
        const val SERVICE_TYPE = "_opendash._tcp."

        /**
         * Default port we advertise in mDNS. No server listens here yet — the
         * protocol server is a separate follow-up PR. 8421 was picked because
         * it sits in the IANA unassigned range and doesn't clash with common
         * dev/smart-home ports (Home Assistant 8123, MQTT 1883, etc.).
         */
        const val DEFAULT_PORT: Int = 8421

        /** Visible for tests: default instance name if caller passes null. */
        internal fun defaultInstanceName(model: String?): String {
            val sanitized = model?.trim()?.takeIf { it.isNotEmpty() } ?: "Android"
            return "OpenDash-$sanitized"
        }
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

    /**
     * Advertise this device on the LAN as `_opendash._tcp.`. Idempotent —
     * calling twice without [unregister] is a no-op. The [port] is advertised,
     * but no server is actually bound to it yet (see class doc).
     *
     * @param port TCP port to advertise. Defaults to [DEFAULT_PORT].
     * @param instanceName Service name (unique within `_opendash._tcp.`).
     *   When null, falls back to `OpenDash-${Build.MODEL}`.
     */
    fun register(port: Int = DEFAULT_PORT, instanceName: String? = null) {
        if (registrationListener != null) return

        val name = instanceName ?: defaultInstanceName(Build.MODEL)
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = name
            serviceType = SERVICE_TYPE
            this.port = port
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                val actualName = serviceInfo.serviceName ?: name
                _registeredName.value = actualName
                Timber.d("mDNS registered as $actualName on port ${serviceInfo.port}")
            }
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Timber.w("mDNS registration failed: $errorCode")
                _registeredName.value = null
            }
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                Timber.d("mDNS unregistered: ${serviceInfo.serviceName}")
                _registeredName.value = null
            }
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Timber.w("mDNS unregistration failed: $errorCode")
            }
        }
        registrationListener = listener
        runCatching {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        }.onFailure {
            Timber.w(it, "mDNS registerService threw")
            registrationListener = null
            _registeredName.value = null
        }
    }

    /** Tear down any in-flight registration. Safe to call when not registered. */
    fun unregister() {
        registrationListener?.let {
            runCatching { nsdManager.unregisterService(it) }
                .onFailure { t -> Timber.w(t, "mDNS unregisterService threw") }
        }
        registrationListener = null
        _registeredName.value = null
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
