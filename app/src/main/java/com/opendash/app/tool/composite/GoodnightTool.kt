package com.opendash.app.tool.composite

import com.opendash.app.tool.ToolCall
import com.opendash.app.tool.ToolExecutor
import com.opendash.app.tool.ToolParameter
import com.opendash.app.tool.ToolResult
import com.opendash.app.tool.ToolSchema
import timber.log.Timber
import java.util.UUID

/**
 * One-shot wind-down: turn off all lights, pause media, cancel any active
 * timers. Returns a brief summary of what happened so the LLM (or fast-path
 * caller) can speak it back.
 */
class GoodnightTool(
    private val executor: () -> ToolExecutor
) : ToolExecutor {

    override suspend fun availableTools(): List<ToolSchema> = listOf(
        ToolSchema(
            name = "goodnight",
            description = "Wind-down: turn off all lights, pause media players, and cancel active timers in one shot.",
            parameters = mapOf(
                "include_lights" to ToolParameter("boolean", "Default true", required = false),
                "include_media" to ToolParameter("boolean", "Default true", required = false),
                "include_timers" to ToolParameter("boolean", "Default true", required = false)
            )
        )
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        if (call.name != "goodnight") {
            return ToolResult(call.id, false, "", "Unknown tool: ${call.name}")
        }
        val includeLights = call.arguments["include_lights"] as? Boolean ?: true
        val includeMedia = call.arguments["include_media"] as? Boolean ?: true
        val includeTimers = call.arguments["include_timers"] as? Boolean ?: true

        val inner = executor()
        val parts = mutableListOf<String>()

        suspend fun callInner(name: String, args: Map<String, Any?>, label: String) {
            try {
                val r = inner.execute(
                    ToolCall(
                        id = "gn_${UUID.randomUUID()}",
                        name = name,
                        arguments = args
                    )
                )
                parts += "\"$label\":" + (if (r.success) "true" else "false")
            } catch (e: Exception) {
                Timber.w(e, "goodnight inner call failed: $name")
                parts += "\"$label\":false"
            }
        }

        if (includeLights) {
            callInner(
                "execute_command",
                mapOf("device_type" to "light", "action" to "turn_off"),
                "lights_off"
            )
        }
        if (includeMedia) {
            callInner(
                "execute_command",
                mapOf("device_type" to "media_player", "action" to "media_pause"),
                "media_paused"
            )
        }
        if (includeTimers) {
            callInner("cancel_all_timers", emptyMap(), "timers_cancelled")
        }

        return ToolResult(call.id, true, "{${parts.joinToString(",")}}")
    }
}
