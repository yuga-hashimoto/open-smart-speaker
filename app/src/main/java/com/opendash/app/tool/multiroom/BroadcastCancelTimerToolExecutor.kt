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
 * `broadcast_cancel_timer` — complement of `broadcast_timer`. Fans a
 * `cancel_timer` envelope out to every discovered peer so every speaker's
 * local timer(s) stop in unison.
 *
 * Optional `id` argument — when absent, blank, or `"all"` the receiver
 * cancels every active timer; when a concrete timer id is passed it
 * narrows the cancel to that one timer on every peer. The spoken
 * confirmation picks singular ("Timer cancelled on N speakers.") for the
 * specific-id path and plural ("Timers cancelled on N speakers.") for
 * cancel-all so the voice response doesn't over-promise.
 */
class BroadcastCancelTimerToolExecutor(
    private val broadcaster: AnnouncementBroadcaster
) : ToolExecutor {

    override suspend fun availableTools(): List<ToolSchema> = listOf(
        ToolSchema(
            name = TOOL_NAME,
            description = "Cancel timer(s) on every OpenDash peer discovered on the LAN " +
                "so every device stops alerting in unison. Use for utterances like " +
                "'cancel timers on all speakers' or '全スピーカーのタイマーを取り消し'. " +
                "Optional id narrows the cancel to a single timer; missing/blank/'all' " +
                "cancels every active timer. Requires multi-room broadcast enabled " +
                "and a shared secret in Settings.",
            parameters = mapOf(
                "id" to ToolParameter(
                    type = "string",
                    description = "Optional specific timer id. Omit or pass 'all' to cancel every active timer.",
                    required = false
                )
            )
        )
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        if (call.name != TOOL_NAME) {
            return ToolResult(call.id, false, "", "Unknown tool: ${call.name}")
        }
        val rawId = (call.arguments["id"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
        val specific = rawId?.takeIf { !it.equals(CANCEL_ALL_SENTINEL, ignoreCase = true) }
        return try {
            val result = broadcaster.broadcastCancelTimer(id = specific)
            if (result.sentCount == 0 && result.failures.any { it.second is SendOutcome.Other }) {
                val reason = (result.failures.first().second as SendOutcome.Other).reason
                Timber.d("broadcast_cancel_timer refused: $reason")
                return ToolResult(call.id, false, "", "Broadcast refused: $reason")
            }
            val spoken = when {
                result.sentCount == 0 -> "No peers found to broadcast to."
                else -> {
                    val verb = if (specific != null) "Timer cancelled" else "Timers cancelled"
                    val total = result.sentCount + result.failures.size
                    val peers = if (result.failures.isEmpty()) {
                        "${result.sentCount} speaker${plural(result.sentCount)}"
                    } else {
                        "${result.sentCount} of $total speakers"
                    }
                    "$verb on $peers."
                }
            }
            ToolResult(
                call.id, true,
                """{"sent":${result.sentCount},"failed":${result.failures.size},"spoken":"$spoken"}"""
            )
        } catch (e: Exception) {
            Timber.w(e, "broadcast_cancel_timer threw")
            ToolResult(call.id, false, "", e.message ?: "Broadcast failed")
        }
    }

    private fun plural(n: Int) = if (n == 1) "" else "s"

    companion object {
        const val TOOL_NAME = "broadcast_cancel_timer"

        /**
         * Argument value that, like a missing/blank id, resolves to
         * cancel-all. Kept here so callers and tests reference a single
         * source of truth for the sentinel.
         */
        const val CANCEL_ALL_SENTINEL = "all"
    }
}
