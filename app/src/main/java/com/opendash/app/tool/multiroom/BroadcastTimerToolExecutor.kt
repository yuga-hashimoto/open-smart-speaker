package com.opendash.app.tool.multiroom

import com.opendash.app.multiroom.AnnouncementBroadcaster
import com.opendash.app.multiroom.SendOutcome
import com.opendash.app.tool.ToolCall
import com.opendash.app.tool.ToolExecutor
import com.opendash.app.tool.ToolParameter
import com.opendash.app.tool.ToolResult
import com.opendash.app.tool.ToolSchema
import timber.log.Timber

/**
 * `broadcast_timer` — start the same `set_timer` on every discovered peer
 * so all speakers alert in unison. Complements the local `set_timer` tool;
 * the LLM is expected to pick this variant when the utterance explicitly
 * names "every speaker" / "all speakers" / "全スピーカー".
 *
 * Missing / non-positive seconds short-circuit with a friendly failure
 * rather than sending a malformed envelope.
 */
class BroadcastTimerToolExecutor(
    private val broadcaster: AnnouncementBroadcaster
) : ToolExecutor {

    override suspend fun availableTools(): List<ToolSchema> = listOf(
        ToolSchema(
            name = TOOL_NAME,
            description = "Start the same timer on every OpenDash peer discovered on the LAN " +
                "so every device alerts in unison. Use for utterances like " +
                "'set a 5-minute timer on every speaker' or '全スピーカーで5分タイマー'. " +
                "Requires multi-room broadcast enabled and a shared secret in Settings.",
            parameters = mapOf(
                "seconds" to ToolParameter(
                    type = "integer",
                    description = "Timer duration in seconds. Must be > 0.",
                    required = true
                ),
                "label" to ToolParameter(
                    type = "string",
                    description = "Optional label/name for the timer (e.g. 'tea', 'laundry').",
                    required = false
                )
            )
        )
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        if (call.name != TOOL_NAME) {
            return ToolResult(call.id, false, "", "Unknown tool: ${call.name}")
        }
        val seconds = when (val raw = call.arguments["seconds"]) {
            is Number -> raw.toInt()
            is String -> raw.toIntOrNull() ?: return ToolResult(
                call.id, false, "", "Missing seconds parameter"
            )
            null -> return ToolResult(call.id, false, "", "Missing seconds parameter")
            else -> return ToolResult(call.id, false, "", "Missing seconds parameter")
        }
        if (seconds <= 0) {
            return ToolResult(call.id, false, "", "Timer seconds must be positive")
        }
        val label = (call.arguments["label"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
        return try {
            val result = broadcaster.broadcastTimer(seconds = seconds, label = label)
            if (result.sentCount == 0 && result.failures.any { it.second is SendOutcome.Other }) {
                val reason = (result.failures.first().second as SendOutcome.Other).reason
                Timber.d("broadcast_timer refused: $reason")
                return ToolResult(call.id, false, "", "Broadcast refused: $reason")
            }
            val spoken = when {
                result.sentCount == 0 -> "No peers found to broadcast to."
                result.failures.isEmpty() ->
                    "Timer set on ${result.sentCount} speaker${plural(result.sentCount)}."
                else ->
                    "Timer set on ${result.sentCount} of ${result.sentCount + result.failures.size} speakers."
            }
            ToolResult(
                call.id, true,
                """{"sent":${result.sentCount},"failed":${result.failures.size},"spoken":"$spoken"}"""
            )
        } catch (e: Exception) {
            Timber.w(e, "broadcast_timer threw")
            ToolResult(call.id, false, "", e.message ?: "Broadcast failed")
        }
    }

    private fun plural(n: Int) = if (n == 1) "" else "s"

    companion object {
        const val TOOL_NAME = "broadcast_timer"
    }
}
