package com.opendash.app.tool.accessibility

import com.opendash.app.tool.ToolCall
import com.opendash.app.tool.ToolExecutor
import com.opendash.app.tool.ToolResult
import com.opendash.app.tool.ToolSchema
import timber.log.Timber

class ScreenToolExecutor(
    private val screenReader: ScreenReader
) : ToolExecutor {

    override suspend fun availableTools(): List<ToolSchema> = listOf(
        ToolSchema(
            name = "read_screen",
            description = "Read text currently visible on the screen. Requires the OpenDash Accessibility Service to be enabled.",
            parameters = emptyMap()
        )
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        return try {
            when (call.name) {
                "read_screen" -> executeRead(call)
                else -> ToolResult(call.id, false, "", "Unknown tool: ${call.name}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Screen tool failed")
            ToolResult(call.id, false, "", e.message ?: "Screen error")
        }
    }

    private fun executeRead(call: ToolCall): ToolResult {
        if (!screenReader.isReady()) {
            return ToolResult(
                call.id, false, "",
                "Accessibility service not enabled. Ask user to enable 'OpenDash' in Settings > Accessibility."
            )
        }

        val snapshot = screenReader.readScreen()
            ?: return ToolResult(call.id, false, "", "No active window")

        val visible = snapshot.visibleTexts.joinToString(",") { """"${it.escapeJson()}"""" }
        val clickable = snapshot.clickableLabels.joinToString(",") { """"${it.escapeJson()}"""" }
        val data = """{"package":"${snapshot.packageName.escapeJson()}","visible_texts":[$visible],"clickable":[$clickable]}"""
        return ToolResult(call.id, true, data)
    }

    private fun String.escapeJson(): String =
        replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")
}
