package com.opensmarthome.speaker.tool.system

import com.opensmarthome.speaker.tool.ToolCall
import com.opensmarthome.speaker.tool.ToolExecutor
import com.opensmarthome.speaker.tool.ToolParameter
import com.opensmarthome.speaker.tool.ToolResult
import com.opensmarthome.speaker.tool.ToolSchema
import timber.log.Timber

/**
 * LLM tool for reading and managing device notifications.
 * Requires NotificationListener permission granted by user.
 */
class NotificationToolExecutor(
    private val notificationProvider: NotificationProvider
) : ToolExecutor {

    override suspend fun availableTools(): List<ToolSchema> = listOf(
        ToolSchema(
            name = "list_notifications",
            description = "List current device notifications. Returns app, title, and text for each.",
            parameters = emptyMap()
        ),
        ToolSchema(
            name = "clear_notifications",
            description = "Dismiss notifications. If package_name is given, only that app's notifications are cleared, otherwise all are cleared.",
            parameters = mapOf(
                "package_name" to ToolParameter("string", "Optional package name to clear", required = false)
            )
        )
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        return try {
            when (call.name) {
                "list_notifications" -> executeList(call)
                "clear_notifications" -> executeClear(call)
                else -> ToolResult(call.id, false, "", "Unknown tool: ${call.name}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Notification tool failed: ${call.name}")
            ToolResult(call.id, false, "", e.message ?: "Execution failed")
        }
    }

    private suspend fun executeList(call: ToolCall): ToolResult {
        if (!notificationProvider.isListenerEnabled()) {
            return ToolResult(
                call.id, false, "",
                "Notification listener permission not granted. Ask user to enable it in Settings > Apps > Notification Access."
            )
        }

        val notifications = notificationProvider.listNotifications()
        val data = notifications.joinToString(",") { n ->
            """{"app":"${n.appName.escapeJson()}","package":"${n.packageName}","title":"${n.title.escapeJson()}","text":"${n.text.escapeJson()}","posted_at":${n.postedAtMs}}"""
        }
        return ToolResult(call.id, true, "[$data]")
    }

    private suspend fun executeClear(call: ToolCall): ToolResult {
        if (!notificationProvider.isListenerEnabled()) {
            return ToolResult(call.id, false, "", "Notification listener not enabled")
        }

        val packageName = call.arguments["package_name"] as? String
        val success = if (packageName.isNullOrBlank()) {
            notificationProvider.clearAll()
        } else {
            notificationProvider.clear(packageName)
        }

        return if (success) {
            ToolResult(call.id, true, """{"cleared":true}""")
        } else {
            ToolResult(call.id, false, "", "Failed to clear notifications")
        }
    }

    private fun String.escapeJson(): String =
        replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ")
}
