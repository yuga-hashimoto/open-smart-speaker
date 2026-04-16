package com.opensmarthome.speaker.tool.system

import com.opensmarthome.speaker.tool.ToolCall
import com.opensmarthome.speaker.tool.ToolExecutor
import com.opensmarthome.speaker.tool.ToolResult
import com.opensmarthome.speaker.tool.ToolSchema
import timber.log.Timber

class DeviceHealthToolExecutor(
    private val provider: DeviceHealthProvider
) : ToolExecutor {

    override suspend fun availableTools(): List<ToolSchema> = listOf(
        ToolSchema(
            name = "get_device_health",
            description = "Get device health: battery %, charging status, temperature, RAM, storage, model, Android version.",
            parameters = emptyMap()
        )
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        return try {
            when (call.name) {
                "get_device_health" -> executeHealth(call)
                else -> ToolResult(call.id, false, "", "Unknown tool: ${call.name}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Health tool failed")
            ToolResult(call.id, false, "", e.message ?: "Health error")
        }
    }

    private suspend fun executeHealth(call: ToolCall): ToolResult {
        val h = provider.snapshot()
        val battery = h.batteryPercent?.toString() ?: "null"
        val temp = h.batteryTemperatureC?.let { "%.1f".format(it) } ?: "null"
        val data = """{"battery_percent":$battery,"is_charging":${h.isCharging},"battery_temp_c":$temp,"ram_total_mb":${h.totalRamMb},"ram_available_mb":${h.availableRamMb},"storage_total_mb":${h.totalStorageMb},"storage_available_mb":${h.availableStorageMb},"model":"${h.model.escapeJson()}","android_version":"${h.androidVersion}"}"""
        return ToolResult(call.id, true, data)
    }

    private fun String.escapeJson(): String =
        replace("\\", "\\\\").replace("\"", "\\\"")
}
