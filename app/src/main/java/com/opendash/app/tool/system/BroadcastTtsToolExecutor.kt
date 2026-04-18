package com.opendash.app.tool.system

import com.opendash.app.multiroom.AnnouncementBroadcaster
import com.opendash.app.multiroom.SendOutcome
import com.opendash.app.tool.ToolCall
import com.opendash.app.tool.ToolExecutor
import com.opendash.app.tool.ToolParameter
import com.opendash.app.tool.ToolResult
import com.opendash.app.tool.ToolSchema
import timber.log.Timber

/**
 * `broadcast_tts` tool — speaks [text] on every discovered peer in the
 * multi-room mesh. Local-only safe default: when the shared secret is
 * missing, returns a friendly failure rather than a cryptographic error.
 *
 * Implemented here (in tool.system) rather than tool.multiroom because
 * the LLM already discovers other system tools in this package; no new
 * discovery surface needed.
 */
class BroadcastTtsToolExecutor(
    private val broadcaster: AnnouncementBroadcaster
) : ToolExecutor {

    override suspend fun availableTools(): List<ToolSchema> = listOf(
        ToolSchema(
            name = "broadcast_tts",
            description = "Speak a message on OpenDash peers discovered on the LAN. " +
                "When `group` is omitted, fans out to every discovered peer; when `group` " +
                "is provided, targets only speakers that belong to that locally-defined group " +
                "(e.g. 'kitchen', 'upstairs'). " +
                "Requires multi-room broadcast to be enabled and a shared secret set in Settings.",
            parameters = mapOf(
                "text" to ToolParameter(
                    type = "string",
                    description = "The message to speak aloud on each peer.",
                    required = true
                ),
                "language" to ToolParameter(
                    type = "string",
                    description = "BCP-47 language tag; defaults to en.",
                    required = false
                ),
                "group" to ToolParameter(
                    type = "string",
                    description = "Optional name of a locally-defined speaker group. " +
                        "When present, only speakers in that group receive the broadcast.",
                    required = false
                )
            )
        )
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        if (call.name != "broadcast_tts") {
            return ToolResult(call.id, false, "", "Unknown tool: ${call.name}")
        }
        val text = (call.arguments["text"] as? String)?.trim().orEmpty()
        if (text.isEmpty()) {
            return ToolResult(call.id, false, "", "Missing text parameter")
        }
        val language = (call.arguments["language"] as? String)?.takeIf { it.isNotBlank() } ?: "en"
        val group = (call.arguments["group"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
        return try {
            val result = if (group != null) {
                broadcaster.broadcastTtsToGroup(groupName = group, text = text, language = language)
            } else {
                broadcaster.broadcastTts(text = text, language = language)
            }
            if (result.sentCount == 0 && result.failures.any { it.second is SendOutcome.Other }) {
                val reason = (result.failures.first().second as SendOutcome.Other).reason
                Timber.d("broadcast_tts refused: $reason")
                return ToolResult(call.id, false, "", "Broadcast refused: $reason")
            }
            val target = group?.let { " (group: $it)" } ?: ""
            val spoken = when {
                result.sentCount == 0 && group != null ->
                    "No speakers in group '$group' are currently reachable."
                result.sentCount == 0 -> "No peers found to broadcast to."
                result.failures.isEmpty() ->
                    "Broadcast sent to ${result.sentCount} speaker${plural(result.sentCount)}$target."
                else ->
                    "Broadcast reached ${result.sentCount} of ${result.sentCount + result.failures.size} speakers$target."
            }
            ToolResult(
                call.id, true,
                """{"sent":${result.sentCount},"failed":${result.failures.size},"spoken":"$spoken"}"""
            )
        } catch (e: Exception) {
            Timber.w(e, "broadcast_tts threw")
            ToolResult(call.id, false, "", e.message ?: "Broadcast failed")
        }
    }

    private fun plural(n: Int) = if (n == 1) "" else "s"
}
