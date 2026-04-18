package com.opendash.app.device.provider.homeassistant

import com.opendash.app.device.model.CommandResult
import com.opendash.app.device.model.Device
import com.opendash.app.device.model.DeviceCapability
import com.opendash.app.device.model.DeviceCommand
import com.opendash.app.device.model.DeviceState
import com.opendash.app.device.model.DeviceType
import com.opendash.app.device.provider.DeviceProvider
import com.opendash.app.homeassistant.client.HomeAssistantClient
import com.opendash.app.homeassistant.model.Entity
import com.opendash.app.homeassistant.model.ServiceCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber

class HomeAssistantDeviceProvider(
    private val haClient: HomeAssistantClient
) : DeviceProvider {

    override val id: String = "homeassistant"
    override val displayName: String = "Home Assistant"

    override suspend fun discover(): List<Device> = getDevices()

    override suspend fun getDevices(): List<Device> =
        haClient.getStates().map { it.toDevice() }

    override suspend fun getDeviceState(deviceId: String): DeviceState =
        haClient.getState(deviceId).toDeviceState()

    override suspend fun executeCommand(command: DeviceCommand): CommandResult {
        val parts = command.deviceId.split(".", limit = 2)
        val domain = parts.getOrElse(0) { "" }
        val result = haClient.callService(
            ServiceCall(
                domain = domain,
                service = command.action,
                entityId = command.deviceId,
                data = command.parameters
            )
        )
        return CommandResult(success = result.success)
    }

    override fun stateChanges(): Flow<DeviceState> =
        haClient.stateChanges().map { it.toDeviceState() }

    override suspend fun isAvailable(): Boolean = haClient.isConnected()

    private fun Entity.toDevice(): Device = Device(
        id = entityId,
        providerId = "homeassistant",
        name = friendlyName,
        room = attributes["area"] as? String,
        type = DeviceType.fromString(domain),
        capabilities = inferCapabilities(),
        state = toDeviceState()
    )

    private fun Entity.toDeviceState(): DeviceState = DeviceState(
        deviceId = entityId,
        isOn = state == "on",
        brightness = (attributes["brightness"] as? Number)?.toFloat(),
        temperature = (attributes["temperature"] as? Number)?.toFloat()
            ?: (attributes["current_temperature"] as? Number)?.toFloat(),
        humidity = (attributes["humidity"] as? Number)?.toFloat(),
        mediaTitle = attributes["media_title"] as? String,
        attributes = attributes
    )

    private fun Entity.inferCapabilities(): Set<DeviceCapability> {
        val caps = mutableSetOf<DeviceCapability>()
        when (domain) {
            "light" -> {
                caps.add(DeviceCapability.ON_OFF)
                if (attributes.containsKey("brightness")) caps.add(DeviceCapability.BRIGHTNESS)
                if (attributes.containsKey("color_temp")) caps.add(DeviceCapability.COLOR_TEMP)
                if (attributes.containsKey("rgb_color")) caps.add(DeviceCapability.RGB_COLOR)
            }
            "switch", "input_boolean" -> caps.add(DeviceCapability.ON_OFF)
            "climate" -> {
                caps.add(DeviceCapability.TEMPERATURE_READ)
                caps.add(DeviceCapability.TEMPERATURE_SET)
                if (attributes.containsKey("humidity")) caps.add(DeviceCapability.HUMIDITY_READ)
            }
            "media_player" -> {
                caps.add(DeviceCapability.ON_OFF)
                caps.add(DeviceCapability.PLAY_PAUSE)
                caps.add(DeviceCapability.VOLUME)
            }
            "cover" -> {
                caps.add(DeviceCapability.ON_OFF)
                caps.add(DeviceCapability.POSITION)
            }
            "fan" -> caps.add(DeviceCapability.ON_OFF)
            "lock" -> caps.add(DeviceCapability.LOCK_UNLOCK)
            "sensor" -> {
                if (attributes.containsKey("temperature")) caps.add(DeviceCapability.TEMPERATURE_READ)
                if (attributes.containsKey("humidity")) caps.add(DeviceCapability.HUMIDITY_READ)
            }
        }
        return caps
    }
}
