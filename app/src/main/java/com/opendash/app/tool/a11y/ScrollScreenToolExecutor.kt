package com.opendash.app.tool.a11y

import com.opendash.app.a11y.A11yServiceHolder
import com.opendash.app.tool.ToolCall
import com.opendash.app.tool.ToolExecutor
import com.opendash.app.tool.ToolParameter
import com.opendash.app.tool.ToolResult
import com.opendash.app.tool.ToolSchema
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scrolls the foreground window in one of four directions by dispatching a
 * swipe gesture across the current window centre via
 * [com.opendash.app.a11y.OpenDashA11yService.performSwipe].
 *
 * Tool name: `scroll_screen`. Requires the OpenDash Accessibility
 * Service to be enabled.
 */
@Singleton
class ScrollScreenToolExecutor @Inject constructor(
    private val holder: A11yServiceHolder
) : ToolExecutor {

    override suspend fun availableTools(): List<ToolSchema> = listOf(
        ToolSchema(
            name = TOOL_NAME,
            description = "Scroll the current foreground window in a direction. " +
                "Direction values: up, down, left, right. Requires the " +
                "OpenDash Accessibility Service to be enabled.",
            parameters = mapOf(
                "direction" to ToolParameter(
                    type = "string",
                    description = "Swipe direction.",
                    required = true,
                    enum = SUPPORTED_DIRECTIONS
                )
            )
        )
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        if (call.name != TOOL_NAME) {
            return ToolResult(call.id, false, "", "Unknown tool: ${call.name}")
        }
        val direction = (call.arguments["direction"] as? String)?.trim()?.lowercase().orEmpty()
        if (direction !in SUPPORTED_DIRECTIONS) {
            return ToolResult(
                call.id, false, "",
                "scroll_screen direction must be one of ${SUPPORTED_DIRECTIONS.joinToString()}. " +
                    "Got: \"$direction\"."
            )
        }
        return try {
            val service = holder.serviceRef
                ?: return ToolResult(
                    call.id, false, "",
                    "The accessibility service isn't enabled. Ask the user to " +
                        "grant it in Settings → Accessibility → OpenDash."
                )
            val ok = service.performSwipe(direction)
            if (ok) {
                ToolResult(call.id, true, "Scrolled $direction.")
            } else {
                ToolResult(
                    call.id, false, "",
                    "Scroll gesture was rejected by the system."
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "scroll_screen failed")
            ToolResult(call.id, false, "", e.message ?: "scroll_screen error")
        }
    }

    companion object {
        const val TOOL_NAME: String = "scroll_screen"
        val SUPPORTED_DIRECTIONS: List<String> = listOf("up", "down", "left", "right")
    }
}
