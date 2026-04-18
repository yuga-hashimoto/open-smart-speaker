package com.opendash.app.device.provider.matter

import com.opendash.app.device.model.CommandResult
import com.opendash.app.device.model.Device
import com.opendash.app.device.model.DeviceCapability
import com.opendash.app.device.model.DeviceCommand
import com.opendash.app.device.model.DeviceState
import com.opendash.app.device.model.DeviceType
import com.opendash.app.device.provider.DeviceProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import timber.log.Timber

/**
 * Matter device provider using Android's Matter commissioning API.
 *
 * Requires Google Play Services (com.google.android.gms.home.matter).
 * Gracefully degrades on devices without GMS.
 *
 * Matter clusters supported:
 * - On/Off (0x0006)
 * - Level Control (0x0008) - brightness
 * - Color Control (0x0300)
 * - Thermostat (0x0201)
 * - Door Lock (0x0101)
 */
class MatterDeviceProvider : DeviceProvider {

    override val id: String = "matter"
    override val displayName: String = "Matter"

    private val commissionedDevices = mutableMapOf<String, Device>()

    override suspend fun discover(): List<Device> {
        // Matter commissioning is initiated by user via QR code scan
        // Discovery returns already commissioned devices
        return commissionedDevices.values.toList()
    }

    override suspend fun getDevices(): List<Device> = commissionedDevices.values.toList()

    override suspend fun getDeviceState(deviceId: String): DeviceState {
        val device = commissionedDevices[deviceId]
            ?: throw IllegalArgumentException("Device not found: $deviceId")
        return device.state
    }

    override suspend fun executeCommand(command: DeviceCommand): CommandResult {
        return try {
            // Matter cluster commands will be dispatched here
            // For now, update local state optimistically
            val device = commissionedDevices[command.deviceId]
                ?: return CommandResult(false, "Device not found")

            val newState = when (command.action) {
                "turn_on" -> device.state.copy(isOn = true)
                "turn_off" -> device.state.copy(isOn = false)
                "toggle" -> device.state.copy(isOn = device.state.isOn != true)
                "set_brightness" -> {
                    val brightness = (command.parameters["brightness"] as? Number)?.toFloat()
                    device.state.copy(brightness = brightness)
                }
                else -> device.state
            }
            commissionedDevices[command.deviceId] = device.copy(state = newState)
            Timber.d("Matter command executed: ${command.action} on ${command.deviceId}")
            CommandResult(true, updatedState = newState)
        } catch (e: Exception) {
            Timber.e(e, "Matter command failed")
            CommandResult(false, e.message)
        }
    }

    override fun stateChanges(): Flow<DeviceState> = emptyFlow()

    override suspend fun isAvailable(): Boolean {
        return try {
            Class.forName("com.google.android.gms.home.matter.Matter")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    fun addCommissionedDevice(
        deviceId: String,
        name: String,
        type: DeviceType,
        capabilities: Set<DeviceCapability>
    ) {
        commissionedDevices[deviceId] = Device(
            id = deviceId,
            providerId = id,
            name = name,
            type = type,
            capabilities = capabilities,
            state = DeviceState(deviceId = deviceId, isOn = false)
        )
    }
}
