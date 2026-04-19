package com.opendash.app.tool.system

import com.opendash.app.tool.ToolCall
import com.opendash.app.tool.ToolExecutor
import com.opendash.app.tool.ToolParameter
import com.opendash.app.tool.ToolResult
import com.opendash.app.tool.ToolSchema
import timber.log.Timber

/**
 * LLM tools to enumerate the user's audio + video library. Useful for
 * "what music do I have?" / "play the latest video I recorded".
 */
class MediaLibraryToolExecutor(
    private val provider: MediaLibraryProvider
) : ToolExecutor {

    override suspend fun availableTools(): List<ToolSchema> = listOf(
        ToolSchema(
            name = "list_recent_audio",
            description = "List the most recently added music tracks on this device.",
            parameters = mapOf(
                "limit" to ToolParameter("number", "Max results (1-100, default 20)", required = false)
            )
        ),
        ToolSchema(
            name = "list_recent_videos",
            description = "List the most recently added videos on this device.",
            parameters = mapOf(
                "limit" to ToolParameter("number", "Max results (1-100, default 20)", required = false)
            )
        )
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        return try {
            when (call.name) {
                "list_recent_audio" -> executeAudio(call)
                "list_recent_videos" -> executeVideos(call)
                else -> ToolResult(call.id, false, "", "Unknown tool: ${call.name}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Media tool failed: ${call.name}")
            ToolResult(call.id, false, "", e.message ?: "Execution failed")
        }
    }

    private suspend fun executeAudio(call: ToolCall): ToolResult {
        if (!provider.hasAudioPermission()) {
            return ToolResult(
                call.id, false, "",
                "Audio permission not granted. Ask user to grant READ_MEDIA_AUDIO."
            )
        }
        val limit = (call.arguments["limit"] as? Number)?.toInt() ?: 20
        val tracks = provider.recentAudio(limit)
        val items = tracks.joinToString(",") { t ->
            """{"uri":"${t.uri.escapeJson()}","title":"${t.title.escapeJson()}","artist":"${t.artist.escapeJson()}","album":"${t.album.escapeJson()}","duration_ms":${t.durationMs},"size_bytes":${t.sizeBytes}}"""
        }
        return ToolResult(call.id, true, "[$items]")
    }

    private suspend fun executeVideos(call: ToolCall): ToolResult {
        if (!provider.hasVideoPermission()) {
            return ToolResult(
                call.id, false, "",
                "Video permission not granted. Ask user to grant READ_MEDIA_VIDEO."
            )
        }
        val limit = (call.arguments["limit"] as? Number)?.toInt() ?: 20
        val videos = provider.recentVideos(limit)
        val items = videos.joinToString(",") { v ->
            """{"uri":"${v.uri.escapeJson()}","name":"${v.name.escapeJson()}","duration_ms":${v.durationMs},"size_bytes":${v.sizeBytes},"width":${v.width},"height":${v.height},"taken_ms":${v.takenMs}}"""
        }
        return ToolResult(call.id, true, "[$items]")
    }

    private fun String.escapeJson(): String =
        replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")
}
