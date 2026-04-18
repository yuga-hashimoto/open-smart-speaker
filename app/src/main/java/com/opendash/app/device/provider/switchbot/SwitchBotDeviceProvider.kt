package com.opendash.app.device.provider.switchbot

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

class SwitchBotDeviceProvider(
    private val apiClient: SwitchBotApiClient
) : DeviceProvider {

    override val id: String = "switchbot"
    override val displayName: String = "SwitchBot"

    override suspend fun discover(): List<Device> = getDevices()

    override suspend fun getDevices(): List<Device> {
        return try {
            apiClient.getDevices().map { it.toDevice() }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get SwitchBot devices")
            emptyList()
        }
    }

    override suspend fun getDeviceState(deviceId: String): DeviceState {
        val status = apiClient.getDeviceStatus(deviceId)
        return DeviceState(
            deviceId = deviceId,
            isOn = status["power"] == "on",
            brightness = (status["brightness"] as? Number)?.toFloat(),
            temperature = (status["temperature"] as? Number)?.toFloat(),
            humidity = (status["humidity"] as? Number)?.toFloat(),
            attributes = status
        )
    }

    override suspend fun executeCommand(command: DeviceCommand): CommandResult {
        val sbCommand = when (command.action) {
            "turn_on" -> "turnOn"
            "turn_off" -> "turnOff"
            "toggle" -> "toggle"
            "set_brightness" -> "setBrightness"
            else -> command.action
        }
        val parameter = when (command.action) {
            "set_brightness" -> (command.parameters["brightness"] as? Number)?.toString() ?: "100"
            else -> "default"
        }
        val success = apiClient.sendCommand(command.deviceId, sbCommand, parameter)
        return CommandResult(success)
    }

    override fun stateChanges(): Flow<DeviceState> = emptyFlow()

    override suspend fun isAvailable(): Boolean {
        return try {
            apiClient.getDevices().isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.toDevice(): Device {
        val deviceId = this["deviceId"] as? String ?: ""
        val deviceName = this["deviceName"] as? String ?: "SwitchBot Device"
        val deviceType = this["deviceType"] as? String ?: ""

        val (type, capabilities) = mapSwitchBotType(deviceType)

        return Device(
            id = "switchbot_$deviceId",
            providerId = "switchbot",
            name = deviceName,
            type = type,
            capabilities = capabilities,
            state = DeviceState(deviceId = "switchbot_$deviceId")
        )
    }

    private fun mapSwitchBotType(deviceType: String): Pair<DeviceType, Set<DeviceCapability>> {
        return when (deviceType.lowercase()) {
            "bot" -> DeviceType.SWITCH to setOf(DeviceCapability.ON_OFF)
            "curtain", "curtain3" -> DeviceType.COVER to setOf(DeviceCapability.ON_OFF, DeviceCapability.POSITION)
            "plug", "plug mini (us)", "plug mini (jp)" -> DeviceType.SWITCH to setOf(DeviceCapability.ON_OFF)
            "meter", "meter plus", "meter pro" -> DeviceType.SENSOR to setOf(DeviceCapability.TEMPERATURE_READ, DeviceCapability.HUMIDITY_READ)
            "hub mini", "hub 2" -> DeviceType.OTHER to emptySet()
            "color bulb" -> DeviceType.LIGHT to setOf(DeviceCapability.ON_OFF, DeviceCapability.BRIGHTNESS, DeviceCapability.RGB_COLOR)
            "strip light" -> DeviceType.LIGHT to setOf(DeviceCapability.ON_OFF, DeviceCapability.BRIGHTNESS)
            "ceiling light" -> DeviceType.LIGHT to setOf(DeviceCapability.ON_OFF, DeviceCapability.BRIGHTNESS, DeviceCapability.COLOR_TEMP)
            "lock", "lock pro" -> DeviceType.LOCK to setOf(DeviceCapability.LOCK_UNLOCK)
            else -> DeviceType.OTHER to setOf(DeviceCapability.ON_OFF)
        }
    }
}
