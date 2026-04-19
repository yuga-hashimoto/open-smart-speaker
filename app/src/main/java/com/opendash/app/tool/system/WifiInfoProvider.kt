package com.opendash.app.tool.system

interface WifiInfoProvider {
    fun isEnabled(): Boolean
    suspend fun current(): WifiSnapshot
}

data class WifiSnapshot(
    val ssid: String?,
    val bssid: String?,
    val rssiDbm: Int?,
    val linkSpeedMbps: Int?,
    val frequencyMhz: Int?,
    val ipAddressV4: String?
)
