package com.opendash.app.tool.system

import com.opendash.app.tool.ToolCall
import com.opendash.app.tool.ToolExecutor
import com.opendash.app.tool.ToolParameter
import com.opendash.app.tool.ToolResult
import com.opendash.app.tool.ToolSchema
import timber.log.Timber

/**
 * LLM tool for replying to messaging notifications (LINE, Messenger, SMS, ...)
 * via the Android `RemoteInput` mechanism. Requires Notification Listener
 * access. Identifies notifications by the `StatusBarNotification.key` string
 * returned from `list_notifications`.
 */
class NotificationReplyToolExecutor(
    private val notificationProvider: NotificationProvider
) : ToolExecutor {

    override suspend fun availableTools(): List<ToolSchema> = listOf(
        ToolSchema(
            name = "reply_to_notification",
            description = "Send a text reply to a messaging notification (LINE, Messenger, SMS, etc.) " +
                "using the notification's reply action. The key is the StatusBarNotification.key " +
                "from list_notifications. Not all notifications expose a reply action.",
            parameters = mapOf(
                "key" to ToolParameter(
                    type = "string",
                    description = "StatusBarNotification.key from list_notifications",
                    required = true
                ),
                "text" to ToolParameter(
                    type = "string",
                    description = "Reply text to send",
                    required = true
                )
            )
        )
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        return try {
            when (call.name) {
                "reply_to_notification" -> executeReply(call)
                else -> ToolResult(call.id, false, "", "Unknown tool: ${call.name}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Notification reply tool failed")
            ToolResult(call.id, false, "", e.message ?: "Reply error")
        }
    }

    private suspend fun executeReply(call: ToolCall): ToolResult {
        if (!notificationProvider.isListenerEnabled()) {
            return ToolResult(
                call.id, false, "",
                "Notification access isn't enabled. Ask the user to grant it in " +
                    "Settings \u2192 Apps \u2192 Special access \u2192 Notification access."
            )
        }

        val key = call.arguments["key"] as? String
            ?: return ToolResult(call.id, false, "", "Missing key")
        val text = call.arguments["text"] as? String
            ?: return ToolResult(call.id, false, "", "Missing text")

        if (key.isBlank()) {
            return ToolResult(call.id, false, "", "key must not be empty")
        }
        if (text.isBlank()) {
            return ToolResult(call.id, false, "", "text must not be empty")
        }

        return when (val outcome = notificationProvider.replyToNotification(key, text)) {
            is ReplyOutcome.Sent -> ToolResult(call.id, true, """{"sent":true}""")
            is ReplyOutcome.ListenerNotConnected -> ToolResult(
                call.id, false, "",
                "Notification listener isn't connected yet. Ask the user to toggle " +
                    "Notification access off and on again."
            )
            is ReplyOutcome.NotFound -> ToolResult(
                call.id, false, "",
                "No active notification matches that key. It may have been dismissed."
            )
            is ReplyOutcome.NoReplyAction -> ToolResult(
                call.id, false, "",
                "This notification doesn't have a reply action. " +
                    "Suggest the user open the app to reply."
            )
            is ReplyOutcome.Failed -> ToolResult(call.id, false, "", outcome.reason)
        }
    }
}
