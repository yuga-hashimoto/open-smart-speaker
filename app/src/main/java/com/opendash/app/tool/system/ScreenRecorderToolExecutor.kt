package com.opendash.app.tool.system

import com.opendash.app.tool.ToolCall
import com.opendash.app.tool.ToolExecutor
import com.opendash.app.tool.ToolParameter
import com.opendash.app.tool.ToolResult
import com.opendash.app.tool.ToolSchema
import timber.log.Timber

/**
 * OpenClaw screen.record equivalent.
 */
class ScreenRecorderToolExecutor(
    private val holder: ScreenRecorderHolder
) : ToolExecutor {

    override suspend fun availableTools(): List<ToolSchema> = listOf(
        ToolSchema(
            name = "start_screen_recording",
            description = "Start recording the device screen. Requires user consent (system dialog). Stops automatically after max_duration_sec or when stop_screen_recording is called.",
            parameters = mapOf(
                "max_duration_sec" to ToolParameter("number", "Max duration in seconds (1-60, default 30)", required = false),
                "include_audio" to ToolParameter("string", "'true' to record mic audio too (default false)", required = false)
            )
        ),
        ToolSchema(
            name = "stop_screen_recording",
            description = "Stop the current screen recording.",
            parameters = emptyMap()
        )
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        return try {
            when (call.name) {
                "start_screen_recording" -> executeStart(call)
                "stop_screen_recording" -> executeStop(call)
                else -> ToolResult(call.id, false, "", "Unknown tool: ${call.name}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Screen recording tool failed")
            ToolResult(call.id, false, "", e.message ?: "Recording error")
        }
    }

    private suspend fun executeStart(call: ToolCall): ToolResult {
        val recorder = holder.current()
        val maxSec = (call.arguments["max_duration_sec"] as? Number)?.toInt()?.coerceIn(1, 60) ?: 30
        val audio = (call.arguments["include_audio"] as? String)?.equals("true", ignoreCase = true) == true

        return when (val r = recorder.start(RecordRequest(maxSec, audio))) {
            is StartResult.Started -> ToolResult(
                call.id, true,
                """{"recording":true,"output":"${r.outputPath}","max_sec":$maxSec}"""
            )
            is StartResult.NeedsUserConsent -> ToolResult(
                call.id, false, "",
                "Screen recording requires user to grant MediaProjection consent. Open the app to trigger the system dialog."
            )
            is StartResult.Failed -> ToolResult(call.id, false, "", r.reason)
        }
    }

    private suspend fun executeStop(call: ToolCall): ToolResult {
        val recorder = holder.current()
        return when (val r = recorder.stop()) {
            is StopResult.Stopped -> ToolResult(
                call.id, true,
                """{"stopped":true,"output":"${r.outputPath}","duration_ms":${r.durationMs}}"""
            )
            is StopResult.NotRecording -> ToolResult(call.id, false, "", "Not currently recording")
            is StopResult.Failed -> ToolResult(call.id, false, "", r.reason)
        }
    }
}
