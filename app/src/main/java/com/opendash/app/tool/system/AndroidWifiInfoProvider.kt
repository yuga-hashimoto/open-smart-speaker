package com.opendash.app.tool.system

import android.content.Context
import android.net.wifi.WifiManager
import timber.log.Timber

@Suppress("DEPRECATION")
class AndroidWifiInfoProvider(
    private val context: Context
) : WifiInfoProvider {

    private val wifiManager: WifiManager? by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    }

    override fun isEnabled(): Boolean = wifiManager?.isWifiEnabled == true

    override suspend fun current(): WifiSnapshot {
        val w = wifiManager ?: return empty()
        return try {
            val info = w.connectionInfo ?: return empty()
            // SSID is wrapped in quotes; strip them. Returns "<unknown ssid>" when
            // the app is not granted location/NEARBY_WIFI_DEVICES on newer SDKs.
            val rawSsid = info.ssid?.trim('"').takeIf { !it.isNullOrBlank() && it != "<unknown ssid>" }
            WifiSnapshot(
                ssid = rawSsid,
                bssid = info.bssid?.takeIf { it != "02:00:00:00:00:00" },
                rssiDbm = info.rssi.takeIf { it != -127 },
                linkSpeedMbps = info.linkSpeed.takeIf { it > 0 },
                frequencyMhz = info.frequency.takeIf { it > 0 },
                ipAddressV4 = formatIpv4(info.ipAddress)
            )
        } catch (e: SecurityException) {
            Timber.w(e, "WiFi info blocked")
            empty()
        } catch (e: Exception) {
            Timber.e(e, "WiFi snapshot failed")
            empty()
        }
    }

    private fun empty() = WifiSnapshot(null, null, null, null, null, null)

    private fun formatIpv4(raw: Int): String? {
        if (raw == 0) return null
        return "${raw and 0xFF}.${(raw shr 8) and 0xFF}.${(raw shr 16) and 0xFF}.${(raw shr 24) and 0xFF}"
    }
}
