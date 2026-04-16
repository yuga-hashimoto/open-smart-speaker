package com.opensmarthome.speaker.tool.system

/**
 * Fetches device GPS location.
 * Requires ACCESS_COARSE_LOCATION or ACCESS_FINE_LOCATION.
 *
 * Inspired by OpenClaw's location.get command.
 */
interface LocationProvider {
    suspend fun getCurrent(desiredAccuracy: Accuracy = Accuracy.BALANCED): LocationResult?
    fun hasPermission(): Boolean

    enum class Accuracy { COARSE, BALANCED, PRECISE }
}

data class LocationResult(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val timestampMs: Long,
    val provider: String
)
