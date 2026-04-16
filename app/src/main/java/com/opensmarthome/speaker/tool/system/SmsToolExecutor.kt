package com.opensmarthome.speaker.tool.system

import com.opensmarthome.speaker.tool.ToolCall
import com.opensmarthome.speaker.tool.ToolExecutor
import com.opensmarthome.speaker.tool.ToolParameter
import com.opensmarthome.speaker.tool.ToolResult
import com.opensmarthome.speaker.tool.ToolSchema
import timber.log.Timber

class SmsToolExecutor(
    private val sender: SmsSender
) : ToolExecutor {

    override suspend fun availableTools(): List<ToolSchema> = listOf(
        ToolSchema(
            name = "send_sms",
            description = "Send an SMS message to a phone number. Requires explicit user confirmation before using.",
            parameters = mapOf(
                "phone_number" to ToolParameter("string", "Destination phone number (E.164 preferred)", required = true),
                "message" to ToolParameter("string", "Message body (long messages auto-split)", required = true)
            )
        )
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        return try {
            when (call.name) {
                "send_sms" -> executeSend(call)
                else -> ToolResult(call.id, false, "", "Unknown tool: ${call.name}")
            }
        } catch (e: Exception) {
            Timber.e(e, "SMS tool failed")
            ToolResult(call.id, false, "", e.message ?: "SMS error")
        }
    }

    private suspend fun executeSend(call: ToolCall): ToolResult {
        val number = call.arguments["phone_number"] as? String
            ?: return ToolResult(call.id, false, "", "Missing phone_number")
        val message = call.arguments["message"] as? String
            ?: return ToolResult(call.id, false, "", "Missing message")

        if (number.isBlank() || message.isBlank()) {
            return ToolResult(call.id, false, "", "phone_number and message must not be empty")
        }

        return when (val result = sender.send(number, message)) {
            is SmsResult.Sent -> ToolResult(
                call.id, true,
                """{"sent":true,"to":"${number.escapeJson()}","length":${message.length}}"""
            )
            is SmsResult.NoPermission -> ToolResult(
                call.id, false, "",
                "SEND_SMS permission not granted. Ask user to enable it."
            )
            is SmsResult.Failed -> ToolResult(call.id, false, "", result.reason)
        }
    }

    private fun String.escapeJson(): String =
        replace("\\", "\\\\").replace("\"", "\\\"")
}
