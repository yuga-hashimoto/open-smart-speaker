package com.opensmarthome.speaker.tool.multiroom

import com.opensmarthome.speaker.assistant.model.AssistantMessage
import com.opensmarthome.speaker.multiroom.AnnouncementBroadcaster
import com.opensmarthome.speaker.multiroom.SendOutcome
import com.opensmarthome.speaker.tool.ToolCall
import com.opensmarthome.speaker.tool.ToolExecutor
import com.opensmarthome.speaker.tool.ToolParameter
import com.opensmarthome.speaker.tool.ToolResult
import com.opensmarthome.speaker.tool.ToolSchema
import timber.log.Timber

/**
 * `handoff_session` (P17.5) — move the current voice conversation to
 * another discovered speaker. Reads the current conversation history from
 * [historyProvider], forwards the last [MAX_MESSAGES] messages to
 * [broadcaster.handoffConversation], and reports success/failure.
 *
 * Media handoff is not yet supported (see TODO in [AnnouncementDispatcher]).
 */
class HandoffToolExecutor(
    private val broadcaster: AnnouncementBroadcaster,
    private val historyProvider: () -> List<AssistantMessage>
) : ToolExecutor {

    override suspend fun availableTools(): List<ToolSchema> = listOf(
        ToolSchema(
            name = TOOL_NAME,
            description = "Move the current voice conversation to another discovered speaker. " +
                "Use for utterances like 'move this to the kitchen speaker' or " +
                "'send to bedroom'. Target is the peer's mDNS service name or a " +
                "friendly alias (prefix match, case-insensitive).",
            parameters = mapOf(
                "target" to ToolParameter(
                    type = "string",
                    description = "Target peer service name or friendly alias (e.g. 'kitchen' or 'speaker-kitchen').",
                    required = true
                )
            )
        )
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        if (call.name != TOOL_NAME) {
            return ToolResult(call.id, false, "", "Unknown tool: ${call.name}")
        }
        val target = (call.arguments["target"] as? String)?.trim().orEmpty()
        if (target.isEmpty()) {
            return ToolResult(call.id, false, "", "Missing required argument: target")
        }
        val messages = historyProvider().takeLast(MAX_MESSAGES)
        return runCatching { broadcaster.handoffConversation(target, messages) }
            .fold(
                onSuccess = { outcome -> outcomeToResult(call.id, target, outcome, messages.size) },
                onFailure = { e ->
                    Timber.w(e, "handoff_session failed")
                    ToolResult(call.id, false, "", e.message ?: "handoff failed")
                }
            )
    }

    private fun outcomeToResult(
        callId: String,
        target: String,
        outcome: SendOutcome,
        messageCount: Int
    ): ToolResult = when (outcome) {
        SendOutcome.Ok -> ToolResult(
            callId = callId,
            success = true,
            data = """{"target":"$target","messages":$messageCount,"spoken":"Moving to $target."}"""
        )
        SendOutcome.Timeout -> ToolResult(
            callId, false, "", "Timed out sending to $target."
        )
        SendOutcome.ConnectionRefused -> ToolResult(
            callId, false, "", "Connection refused by $target."
        )
        is SendOutcome.Other -> ToolResult(
            callId, false, "", outcome.reason
        )
    }

    companion object {
        const val TOOL_NAME = "handoff_session"

        /**
         * Keep the handoff payload bounded — 5 messages covers "the user
         * said X, the assistant said Y, follow-up X', follow-up Y',
         * current user turn" which is plenty to resume conversation
         * context without bloating the envelope.
         */
        const val MAX_MESSAGES = 5
    }
}
