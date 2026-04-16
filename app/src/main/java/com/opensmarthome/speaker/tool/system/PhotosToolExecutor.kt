package com.opensmarthome.speaker.tool.system

import com.opensmarthome.speaker.tool.ToolCall
import com.opensmarthome.speaker.tool.ToolExecutor
import com.opensmarthome.speaker.tool.ToolParameter
import com.opensmarthome.speaker.tool.ToolResult
import com.opensmarthome.speaker.tool.ToolSchema
import timber.log.Timber

class PhotosToolExecutor(
    private val provider: PhotosProvider
) : ToolExecutor {

    override suspend fun availableTools(): List<ToolSchema> = listOf(
        ToolSchema(
            name = "list_recent_photos",
            description = "List the most recently taken photos (URIs + metadata).",
            parameters = mapOf(
                "limit" to ToolParameter("number", "Max results (1-50, default 10)", required = false)
            )
        )
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        return try {
            when (call.name) {
                "list_recent_photos" -> executeList(call)
                else -> ToolResult(call.id, false, "", "Unknown tool: ${call.name}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Photos tool failed")
            ToolResult(call.id, false, "", e.message ?: "Photos error")
        }
    }

    private suspend fun executeList(call: ToolCall): ToolResult {
        if (!provider.hasPermission()) {
            return ToolResult(call.id, false, "", "Photo access permission not granted (READ_MEDIA_IMAGES)")
        }
        val limit = (call.arguments["limit"] as? Number)?.toInt()?.coerceIn(1, 50) ?: 10
        val photos = provider.getLatest(limit)
        val data = photos.joinToString(",") { p ->
            """{"uri":"${p.uri}","name":"${p.displayName.escapeJson()}","taken_at":${p.takenAtMs},"width":${p.widthPx},"height":${p.heightPx},"size":${p.sizeBytes}}"""
        }
        return ToolResult(call.id, true, "[$data]")
    }

    private fun String.escapeJson(): String =
        replace("\\", "\\\\").replace("\"", "\\\"")
}
