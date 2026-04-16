package com.opensmarthome.speaker.tool.system

import com.opensmarthome.speaker.tool.ToolCall
import com.opensmarthome.speaker.tool.ToolExecutor
import com.opensmarthome.speaker.tool.ToolParameter
import com.opensmarthome.speaker.tool.ToolResult
import com.opensmarthome.speaker.tool.ToolSchema
import timber.log.Timber

/**
 * LLM tool for getting the device's GPS location.
 * Inspired by OpenClaw's location.get command.
 */
class LocationToolExecutor(
    private val locationProvider: LocationProvider
) : ToolExecutor {

    override suspend fun availableTools(): List<ToolSchema> = listOf(
        ToolSchema(
            name = "get_location",
            description = "Get the device's current GPS location. Returns latitude, longitude, and accuracy in meters.",
            parameters = mapOf(
                "accuracy" to ToolParameter(
                    "string",
                    "Desired accuracy: 'coarse', 'balanced' (default), or 'precise'",
                    required = false
                )
            )
        )
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        return try {
            when (call.name) {
                "get_location" -> executeGetLocation(call)
                else -> ToolResult(call.id, false, "", "Unknown tool: ${call.name}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Location tool failed: ${call.name}")
            ToolResult(call.id, false, "", e.message ?: "Execution failed")
        }
    }

    private suspend fun executeGetLocation(call: ToolCall): ToolResult {
        if (!locationProvider.hasPermission()) {
            return ToolResult(
                call.id, false, "",
                "Location permission not granted. Ask user to grant ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION."
            )
        }

        val accuracy = when ((call.arguments["accuracy"] as? String)?.lowercase()) {
            "coarse" -> LocationProvider.Accuracy.COARSE
            "precise" -> LocationProvider.Accuracy.PRECISE
            else -> LocationProvider.Accuracy.BALANCED
        }

        val result = locationProvider.getCurrent(accuracy)
            ?: return ToolResult(call.id, false, "", "Unable to get location (no provider enabled or timeout)")

        val data = """{"latitude":${result.latitude},"longitude":${result.longitude},"accuracy_m":${result.accuracyMeters},"timestamp_ms":${result.timestampMs},"provider":"${result.provider}"}"""
        return ToolResult(call.id, true, data)
    }
}
