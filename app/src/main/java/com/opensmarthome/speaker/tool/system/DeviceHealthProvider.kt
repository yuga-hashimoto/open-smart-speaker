package com.opensmarthome.speaker.tool.system

/**
 * Reports device health: battery, memory, storage.
 * OpenClaw device.health equivalent.
 */
interface DeviceHealthProvider {
    suspend fun snapshot(): DeviceHealth
}

data class DeviceHealth(
    val batteryPercent: Int?,
    val isCharging: Boolean,
    val batteryTemperatureC: Float?,
    val totalRamMb: Long,
    val availableRamMb: Long,
    val totalStorageMb: Long,
    val availableStorageMb: Long,
    val model: String,
    val androidVersion: String
)
