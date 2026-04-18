package com.opendash.app.tool.system

import com.opendash.app.tool.ToolCall
import com.opendash.app.tool.ToolExecutor
import com.opendash.app.tool.ToolParameter
import com.opendash.app.tool.ToolResult
import com.opendash.app.tool.ToolSchema
import timber.log.Timber

/**
 * LLM tool for taking a photo. The actual capture implementation is provided
 * by whatever CameraProvider is currently registered in the holder.
 *
 * This keeps the tool layer decoupled from CameraX / Camera2 specifics while
 * letting Activities plug in a real implementation when the UI is foregrounded.
 *
 * OpenClaw camera.snap equivalent.
 */
class CameraToolExecutor(
    private val holder: CameraProviderHolder
) : ToolExecutor {

    override suspend fun availableTools(): List<ToolSchema> = listOf(
        ToolSchema(
            name = "take_photo",
            description = "Take a photo with the device camera. Requires the app to be in foreground with a registered camera provider.",
            parameters = mapOf(
                "facing" to ToolParameter(
                    "string",
                    "Camera to use: 'front' or 'back' (default 'back')",
                    required = false
                ),
                "max_width" to ToolParameter(
                    "number",
                    "Max image width in pixels (default 1920)",
                    required = false
                )
            )
        )
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        return try {
            when (call.name) {
                "take_photo" -> executeCapture(call)
                else -> ToolResult(call.id, false, "", "Unknown tool: ${call.name}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Camera tool failed")
            ToolResult(call.id, false, "", e.message ?: "Camera error")
        }
    }

    private suspend fun executeCapture(call: ToolCall): ToolResult {
        val provider = holder.current()
        if (!provider.isReady()) {
            return ToolResult(
                call.id, false, "",
                "Camera not ready. Open the app and ensure a camera-capable screen is in foreground."
            )
        }

        val facing = when ((call.arguments["facing"] as? String)?.lowercase()) {
            "front" -> CaptureRequest.Facing.FRONT
            else -> CaptureRequest.Facing.BACK
        }
        val maxWidth = (call.arguments["max_width"] as? Number)?.toInt() ?: 1920

        return when (val result = provider.capture(CaptureRequest(facing, maxWidth))) {
            is CaptureResult.Success -> ToolResult(
                call.id, true,
                """{"captured":true,"mime_type":"${result.mimeType}","size_bytes":${result.imageBytes.size}}"""
            )
            is CaptureResult.NotReady -> ToolResult(
                call.id, false, "", "Camera became unavailable during capture"
            )
            is CaptureResult.Failed -> ToolResult(call.id, false, "", result.reason)
        }
    }
}
