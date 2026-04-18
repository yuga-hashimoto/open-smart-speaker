package com.opendash.app.device.provider.mqtt

import com.opendash.app.device.model.CommandResult
import com.opendash.app.device.model.Device
import com.opendash.app.device.model.DeviceCapability
import com.opendash.app.device.model.DeviceCommand
import com.opendash.app.device.model.DeviceState
import com.opendash.app.device.model.DeviceType
import com.opendash.app.device.provider.DeviceProvider
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * MQTT device provider supporting Tasmota/Shelly discovery protocol.
 *
 * Subscribes to homeassistant/+/+/config for auto-discovery.
 * State updates via device-specific state topics.
 * Commands via device-specific command topics.
 */
class MqttDeviceProvider(
    private val mqttClient: MqttClientWrapper,
    private val moshi: Moshi
) : DeviceProvider {

    override val id: String = "mqtt"
    override val displayName: String = "MQTT"

    private val discoveredDevices = mutableMapOf<String, MqttDevice>()
    private val stateChangeChannel = Channel<DeviceState>(Channel.BUFFERED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    data class MqttDevice(
        val device: Device,
        val stateTopic: String,
        val commandTopic: String
    )

    override suspend fun discover(): List<Device> {
        if (!mqttClient.isConnected()) {
            mqttClient.connect()
        }
        // Subscribe to MQTT discovery topics
        mqttClient.subscribe("homeassistant/+/+/config")

        scope.launch {
            mqttClient.messages.collect { msg ->
                when {
                    msg.topic.startsWith("homeassistant/") && msg.topic.endsWith("/config") -> {
                        handleDiscoveryMessage(msg)
                    }
                    else -> {
                        handleStateUpdate(msg)
                    }
                }
            }
        }

        return discoveredDevices.values.map { it.device }
    }

    override suspend fun getDevices(): List<Device> = discoveredDevices.values.map { it.device }

    override suspend fun getDeviceState(deviceId: String): DeviceState {
        return discoveredDevices[deviceId]?.device?.state
            ?: throw IllegalArgumentException("Device not found: $deviceId")
    }

    override suspend fun executeCommand(command: DeviceCommand): CommandResult {
        val mqttDevice = discoveredDevices[command.deviceId]
            ?: return CommandResult(false, "Device not found")

        val payload = when (command.action) {
            "turn_on" -> "ON"
            "turn_off" -> "OFF"
            "toggle" -> "TOGGLE"
            "set_brightness" -> (command.parameters["brightness"] as? Number)?.toString() ?: "100"
            else -> command.action.uppercase()
        }

        mqttClient.publish(mqttDevice.commandTopic, payload)
        return CommandResult(true)
    }

    override fun stateChanges(): Flow<DeviceState> = stateChangeChannel.receiveAsFlow()

    override suspend fun isAvailable(): Boolean = mqttClient.isConnected()

    @Suppress("UNCHECKED_CAST")
    private fun handleDiscoveryMessage(msg: MqttIncomingMessage) {
        try {
            val config = moshi.adapter(Map::class.java).fromJson(msg.payload) as? Map<String, Any?> ?: return
            val parts = msg.topic.split("/")
            if (parts.size < 4) return

            val domain = parts[1]
            val deviceId = "mqtt_${config["unique_id"] ?: parts[2]}"
            val name = config["name"] as? String ?: deviceId
            val stateTopic = config["state_topic"] as? String ?: return
            val commandTopic = config["command_topic"] as? String ?: ""

            val (type, caps) = mapMqttDomain(domain)

            val device = Device(
                id = deviceId,
                providerId = id,
                name = name,
                type = type,
                capabilities = caps,
                state = DeviceState(deviceId = deviceId)
            )

            discoveredDevices[deviceId] = MqttDevice(device, stateTopic, commandTopic)
            mqttClient.subscribe(stateTopic)
            Timber.d("MQTT device discovered: $name ($deviceId)")
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse MQTT discovery message")
        }
    }

    private fun handleStateUpdate(msg: MqttIncomingMessage) {
        val matchingDevice = discoveredDevices.values.find { it.stateTopic == msg.topic } ?: return
        val isOn = msg.payload.uppercase() in listOf("ON", "1", "TRUE")
        val newState = DeviceState(
            deviceId = matchingDevice.device.id,
            isOn = isOn,
            attributes = mapOf("raw_state" to msg.payload)
        )
        discoveredDevices[matchingDevice.device.id] = matchingDevice.copy(
            device = matchingDevice.device.copy(state = newState)
        )
        stateChangeChannel.trySend(newState)
    }

    private fun mapMqttDomain(domain: String): Pair<DeviceType, Set<DeviceCapability>> {
        return when (domain) {
            "light" -> DeviceType.LIGHT to setOf(DeviceCapability.ON_OFF, DeviceCapability.BRIGHTNESS)
            "switch" -> DeviceType.SWITCH to setOf(DeviceCapability.ON_OFF)
            "climate" -> DeviceType.CLIMATE to setOf(DeviceCapability.TEMPERATURE_READ, DeviceCapability.TEMPERATURE_SET)
            "cover" -> DeviceType.COVER to setOf(DeviceCapability.ON_OFF, DeviceCapability.POSITION)
            "fan" -> DeviceType.FAN to setOf(DeviceCapability.ON_OFF)
            "lock" -> DeviceType.LOCK to setOf(DeviceCapability.LOCK_UNLOCK)
            "sensor" -> DeviceType.SENSOR to setOf(DeviceCapability.TEMPERATURE_READ)
            else -> DeviceType.OTHER to setOf(DeviceCapability.ON_OFF)
        }
    }
}
