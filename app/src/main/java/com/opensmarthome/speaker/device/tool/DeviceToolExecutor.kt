package com.opensmarthome.speaker.device.tool

import com.opensmarthome.speaker.device.DeviceManager
import com.opensmarthome.speaker.device.model.DeviceCommand
import com.opensmarthome.speaker.device.model.DeviceType
import com.opensmarthome.speaker.tool.ToolCall
import com.opensmarthome.speaker.tool.ToolExecutor
import com.opensmarthome.speaker.tool.ToolParameter
import com.opensmarthome.speaker.tool.ToolResult
import com.opensmarthome.speaker.tool.ToolSchema
import com.squareup.moshi.Moshi
import timber.log.Timber

class DeviceToolExecutor(
    private val deviceManager: DeviceManager,
    private val moshi: Moshi
) : ToolExecutor {

    override suspend fun availableTools(): List<ToolSchema> = listOf(
        ToolSchema(
            name = "get_device_state",
            description = "Get the current state of a smart home device",
            parameters = mapOf(
                "device_id" to ToolParameter("string", "The device ID", required = true)
            )
        ),
        ToolSchema(
            name = "get_devices_by_type",
            description = "List all devices of a specific type (light, switch, climate, media_player, sensor, cover, fan, lock)",
            parameters = mapOf(
                "type" to ToolParameter("string", "The device type", required = true,
                    enum = DeviceType.entries.map { it.value })
            )
        ),
        ToolSchema(
            name = "get_devices_by_room",
            description = "List all devices in a specific room",
            parameters = mapOf(
                "room" to ToolParameter("string", "The room name", required = true)
            )
        ),
        ToolSchema(
            name = "execute_command",
            description = "Execute a command on a smart home device. Provide either device_id for a single device, or device_type to apply to all devices of that type (e.g. all lights, all media players).",
            parameters = mapOf(
                "device_id" to ToolParameter("string", "The device ID (optional if device_type is provided)", required = false),
                "device_type" to ToolParameter("string", "Device type to target as a group: light, switch, climate, media_player, cover, fan (optional if device_id is provided)", required = false),
                "action" to ToolParameter("string", "The action to perform (turn_on, turn_off, toggle, media_play, media_pause, media_next_track, media_previous_track, set_brightness, set_temperature, etc.)", required = true),
                "parameters" to ToolParameter("object", "Additional parameters as JSON", required = false)
            )
        ),
        ToolSchema(
            name = "get_rooms",
            description = "List all rooms in the smart home",
            parameters = emptyMap()
        )
    )

    @Suppress("UNCHECKED_CAST")
    override suspend fun execute(call: ToolCall): ToolResult {
        return try {
            when (call.name) {
                "get_device_state" -> executeGetDeviceState(call)
                "get_devices_by_type" -> executeGetDevicesByType(call)
                "get_devices_by_room" -> executeGetDevicesByRoom(call)
                "execute_command" -> executeCommand(call)
                "get_rooms" -> executeGetRooms()
                else -> ToolResult(call.id, false, "", "Unknown tool: ${call.name}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Device tool execution failed: ${call.name}")
            ToolResult(call.id, false, "", e.message ?: "Execution failed")
        }
    }

    private fun executeGetDeviceState(call: ToolCall): ToolResult {
        val deviceId = call.arguments["device_id"] as? String
            ?: return ToolResult(call.id, false, "", "Missing device_id")
        val device = deviceManager.getDevice(deviceId)
            ?: return ToolResult(call.id, false, "", "Device not found: $deviceId")
        val data = mapOf(
            "device_id" to device.id,
            "name" to device.name,
            "type" to device.type.value,
            "room" to device.room,
            "is_on" to device.state.isOn,
            "brightness" to device.state.brightness,
            "temperature" to device.state.temperature,
            "humidity" to device.state.humidity,
            "media_title" to device.state.mediaTitle
        )
        return ToolResult(call.id, true, toJson(data))
    }

    private fun executeGetDevicesByType(call: ToolCall): ToolResult {
        val typeStr = call.arguments["type"] as? String
            ?: return ToolResult(call.id, false, "", "Missing type")
        val type = DeviceType.fromString(typeStr)
        val devices = deviceManager.getDevicesByType(type).map { d ->
            mapOf("device_id" to d.id, "name" to d.name, "room" to d.room, "is_on" to d.state.isOn)
        }
        return ToolResult(call.id, true, toJson(devices))
    }

    private fun executeGetDevicesByRoom(call: ToolCall): ToolResult {
        val room = call.arguments["room"] as? String
            ?: return ToolResult(call.id, false, "", "Missing room")
        val devices = deviceManager.getDevicesByRoom(room).map { d ->
            mapOf("device_id" to d.id, "name" to d.name, "type" to d.type.value, "is_on" to d.state.isOn)
        }
        return ToolResult(call.id, true, toJson(devices))
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun executeCommand(call: ToolCall): ToolResult {
        val action = call.arguments["action"] as? String
            ?: return ToolResult(call.id, false, "", "Missing action")
        val params = call.arguments["parameters"] as? Map<String, Any?> ?: emptyMap()
        val deviceId = call.arguments["device_id"] as? String
        val deviceType = call.arguments["device_type"] as? String

        if (!deviceId.isNullOrBlank()) {
            val result = deviceManager.executeCommand(
                DeviceCommand(deviceId = deviceId, action = action, parameters = params)
            )
            return if (result.success) {
                ToolResult(call.id, true, "Command $action executed on $deviceId successfully")
            } else {
                ToolResult(call.id, false, "", result.message ?: "Command failed")
            }
        }

        if (!deviceType.isNullOrBlank()) {
            val type = DeviceType.fromString(deviceType)
            val targets = deviceManager.getDevicesByType(type)
            if (targets.isEmpty()) {
                return ToolResult(call.id, false, "", "No devices of type $deviceType")
            }
            var successes = 0
            var failures = 0
            for (device in targets) {
                val r = deviceManager.executeCommand(
                    DeviceCommand(deviceId = device.id, action = action, parameters = params)
                )
                if (r.success) successes++ else failures++
            }
            val text = "Command $action: $successes ok, $failures failed (${targets.size} ${deviceType}s)"
            return ToolResult(call.id, successes > 0, text)
        }

        return ToolResult(call.id, false, "", "Provide device_id or device_type")
    }

    private fun executeGetRooms(): ToolResult {
        val rooms = deviceManager.getRooms().map { mapOf("id" to it.id, "name" to it.name) }
        return ToolResult("", true, toJson(rooms))
    }

    private fun toJson(data: Any): String {
        return try {
            moshi.adapter(Any::class.java).toJson(data) ?: "{}"
        } catch (e: Exception) {
            "{}"
        }
    }
}
