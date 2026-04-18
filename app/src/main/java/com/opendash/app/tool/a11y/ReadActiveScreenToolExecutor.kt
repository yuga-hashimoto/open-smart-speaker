package com.opendash.app.tool.a11y

import com.opendash.app.a11y.A11yServiceHolder
import com.opendash.app.a11y.NodeSummary
import com.opendash.app.tool.ToolCall
import com.opendash.app.tool.ToolExecutor
import com.opendash.app.tool.ToolResult
import com.opendash.app.tool.ToolSchema
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exposes the current foreground window to the LLM as a markdown-ish flat
 * list of actionable nodes, via [com.opendash.app.a11y.OpenDashA11yService].
 *
 * Tool name: `read_active_screen`. Distinct from the legacy `read_screen`
 * tool (which returns a JSON blob via the older accessibility service).
 *
 * Returns a friendly "please enable accessibility" error if the service
 * isn't currently bound.
 */
@Singleton
class ReadActiveScreenToolExecutor @Inject constructor(
    private val holder: A11yServiceHolder
) : ToolExecutor {

    override suspend fun availableTools(): List<ToolSchema> = listOf(
        ToolSchema(
            name = TOOL_NAME,
            description = "Dump the current foreground window's accessibility node " +
                "tree as a flat markdown list so the agent can reason about " +
                "the screen. Requires the OpenDash Accessibility Service " +
                "(new-style) to be enabled.",
            parameters = emptyMap()
        )
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        if (call.name != TOOL_NAME) {
            return ToolResult(call.id, false, "", "Unknown tool: ${call.name}")
        }
        return try {
            val service = holder.serviceRef
                ?: return ToolResult(
                    call.id, false, "",
                    "The accessibility service isn't enabled. Ask the user to " +
                        "grant it in Settings → Accessibility → OpenDash."
                )

            val nodes = service.dumpActiveWindow()
            val packageName = holder.currentPackage.value
                ?: service.currentForegroundPackage()
            val formatted = formatDump(packageName, nodes)
            ToolResult(call.id, true, formatted)
        } catch (e: Exception) {
            Timber.e(e, "read_active_screen failed")
            ToolResult(call.id, false, "", e.message ?: "read_active_screen error")
        }
    }

    companion object {
        const val TOOL_NAME: String = "read_active_screen"

        /**
         * Formats a dump as a markdown-ish flat list:
         *
         * ```
         * # Screen: com.example.app
         * - [button] "Save" (clickable)
         * - [text] "Your email:"
         * - [edit-text] "current@email.com" (editable)
         * ```
         */
        fun formatDump(packageName: String?, nodes: List<NodeSummary>): String {
            val header = "# Screen: ${packageName ?: "(unknown)"}"
            if (nodes.isEmpty()) {
                return "$header\n(no visible text on screen)"
            }
            val lines = nodes.joinToString("\n") { node -> renderLine(node) }
            return "$header\n$lines"
        }

        private fun renderLine(node: NodeSummary): String {
            val label = (node.text ?: node.contentDescription ?: "").let { escape(it) }
            val attrs = buildList {
                if (node.clickable) add("clickable")
                if (node.role == "edit-text") add("editable")
            }
            val attrSuffix = if (attrs.isEmpty()) "" else " (${attrs.joinToString(", ")})"
            return "- [${node.role}] \"$label\"$attrSuffix"
        }

        private fun escape(text: String): String =
            text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")
    }
}
