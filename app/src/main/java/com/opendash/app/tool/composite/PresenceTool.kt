package com.opendash.app.tool.composite

import com.opendash.app.tool.ToolCall
import com.opendash.app.tool.ToolExecutor
import com.opendash.app.tool.ToolParameter
import com.opendash.app.tool.ToolResult
import com.opendash.app.tool.ToolSchema
import timber.log.Timber
import java.util.UUID

/**
 * "I'm home" / "leaving" presence shortcuts. Mirror Alexa's "I'm home"
 * routine — turn lights on, optionally unmute, optionally bring up media —
 * and the inverse for leaving.
 *
 * Composite — calls back into the same CompositeToolExecutor via lambda
 * to avoid a Hilt cycle.
 */
class PresenceTool(
    private val executor: () -> ToolExecutor
) : ToolExecutor {

    override suspend fun availableTools(): List<ToolSchema> = listOf(
        ToolSchema(
            name = "arrive_home",
            description = "User is arriving home: turn on the lights and unmute audio.",
            parameters = mapOf(
                "include_lights" to ToolParameter("boolean", "Default true", required = false),
                "include_volume" to ToolParameter("boolean", "Default true", required = false)
            )
        ),
        ToolSchema(
            name = "leave_home",
            description = "User is leaving: turn off all lights and pause media.",
            parameters = mapOf(
                "include_lights" to ToolParameter("boolean", "Default true", required = false),
                "include_media" to ToolParameter("boolean", "Default true", required = false)
            )
        )
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        return when (call.name) {
            "arrive_home" -> arriveHome(call)
            "leave_home" -> leaveHome(call)
            else -> ToolResult(call.id, false, "", "Unknown tool: ${call.name}")
        }
    }

    private suspend fun arriveHome(call: ToolCall): ToolResult {
        val includeLights = call.arguments["include_lights"] as? Boolean ?: true
        val includeVolume = call.arguments["include_volume"] as? Boolean ?: true
        val parts = mutableListOf<String>()
        val inner = executor()
        if (includeLights) {
            parts += run("execute_command", mapOf("device_type" to "light", "action" to "turn_on"), inner, "lights_on")
        }
        if (includeVolume) {
            parts += run("set_volume", mapOf("level" to 50.0), inner, "volume_50")
        }
        return ToolResult(call.id, true, "{${parts.joinToString(",")}}")
    }

    private suspend fun leaveHome(call: ToolCall): ToolResult {
        val includeLights = call.arguments["include_lights"] as? Boolean ?: true
        val includeMedia = call.arguments["include_media"] as? Boolean ?: true
        val parts = mutableListOf<String>()
        val inner = executor()
        if (includeLights) {
            parts += run("execute_command", mapOf("device_type" to "light", "action" to "turn_off"), inner, "lights_off")
        }
        if (includeMedia) {
            parts += run("execute_command", mapOf("device_type" to "media_player", "action" to "media_pause"), inner, "media_paused")
        }
        return ToolResult(call.id, true, "{${parts.joinToString(",")}}")
    }

    private suspend fun run(
        name: String,
        args: Map<String, Any?>,
        inner: ToolExecutor,
        label: String
    ): String = try {
        val r = inner.execute(
            ToolCall(id = "presence_${UUID.randomUUID()}", name = name, arguments = args)
        )
        "\"$label\":" + (if (r.success) "true" else "false")
    } catch (e: Exception) {
        Timber.w(e, "presence inner call failed: $name")
        "\"$label\":false"
    }
}
