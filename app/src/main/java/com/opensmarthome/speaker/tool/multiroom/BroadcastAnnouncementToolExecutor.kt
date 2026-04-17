package com.opensmarthome.speaker.tool.multiroom

import com.opensmarthome.speaker.multiroom.AnnouncementBroadcaster
import com.opensmarthome.speaker.multiroom.AnnouncementDispatcher
import com.opensmarthome.speaker.multiroom.SendOutcome
import com.opensmarthome.speaker.tool.ToolCall
import com.opensmarthome.speaker.tool.ToolExecutor
import com.opensmarthome.speaker.tool.ToolParameter
import com.opensmarthome.speaker.tool.ToolResult
import com.opensmarthome.speaker.tool.ToolSchema
import timber.log.Timber

/**
 * `broadcast_announcement` tool — persistent variant of `broadcast_tts`.
 *
 * Receivers both speak the message AND surface it as a banner on the
 * Ambient screen for `ttl_seconds` so anyone who walks into the room
 * during the banner window sees the announcement. Modelled on Alexa's
 * household announcements.
 *
 * Kept separate from `broadcast_tts` because the LLM's prompt shouldn't
 * have to guess "speak once or persist" from a parameter — two tools,
 * two intents.
 */
class BroadcastAnnouncementToolExecutor(
    private val broadcaster: AnnouncementBroadcaster
) : ToolExecutor {

    override suspend fun availableTools(): List<ToolSchema> = listOf(
        ToolSchema(
            name = TOOL_NAME,
            description = "Broadcast a persistent announcement to all OpenSmartSpeaker peers on the LAN. " +
                "Each peer speaks the message AND shows it as a banner on its Ambient screen for " +
                "`ttl_seconds` (default ${AnnouncementBroadcaster.DEFAULT_ANNOUNCEMENT_TTL_SECONDS}s, " +
                "clamped to ${AnnouncementDispatcher.TTL_MIN_SECONDS}..${AnnouncementDispatcher.TTL_MAX_SECONDS}s). " +
                "Use this for household-wide notices (\"dinner's ready\", \"we're leaving in 10 min\") " +
                "where someone who walks into the room later should still see the message. " +
                "Use `broadcast_tts` instead when you only want a speak-once notification.",
            parameters = mapOf(
                "text" to ToolParameter(
                    type = "string",
                    description = "The announcement text to speak and display.",
                    required = true
                ),
                "ttl_seconds" to ToolParameter(
                    type = "integer",
                    description = "Seconds to keep the banner visible on the Ambient screen. " +
                        "Defaults to ${AnnouncementBroadcaster.DEFAULT_ANNOUNCEMENT_TTL_SECONDS}. " +
                        "Clamped to ${AnnouncementDispatcher.TTL_MIN_SECONDS}..${AnnouncementDispatcher.TTL_MAX_SECONDS}.",
                    required = false
                )
            )
        )
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        if (call.name != TOOL_NAME) {
            return ToolResult(call.id, false, "", "Unknown tool: ${call.name}")
        }
        val text = (call.arguments["text"] as? String)?.trim().orEmpty()
        if (text.isEmpty()) {
            return ToolResult(call.id, false, "", "Missing text parameter")
        }
        val ttlRaw = (call.arguments["ttl_seconds"] as? Number)?.toInt()
            ?: AnnouncementBroadcaster.DEFAULT_ANNOUNCEMENT_TTL_SECONDS
        val ttl = ttlRaw.coerceIn(
            AnnouncementDispatcher.TTL_MIN_SECONDS,
            AnnouncementDispatcher.TTL_MAX_SECONDS
        )

        return try {
            val result = broadcaster.broadcastAnnouncement(text = text, ttlSeconds = ttl)
            if (result.sentCount == 0 && result.failures.any { it.second is SendOutcome.Other }) {
                val reason = (result.failures.first().second as SendOutcome.Other).reason
                Timber.d("broadcast_announcement refused: $reason")
                return ToolResult(call.id, false, "", "Announcement refused: $reason")
            }
            val spoken = when {
                result.sentCount == 0 -> "No peers found to announce to."
                result.failures.isEmpty() ->
                    "Announcement sent to ${result.sentCount} speaker${plural(result.sentCount)} (${ttl}s banner)."
                else ->
                    "Announcement reached ${result.sentCount} of ${result.sentCount + result.failures.size} speakers (${ttl}s banner)."
            }
            ToolResult(
                call.id, true,
                """{"sent":${result.sentCount},"failed":${result.failures.size},"ttl_seconds":$ttl,"spoken":"$spoken"}"""
            )
        } catch (e: Exception) {
            Timber.w(e, "broadcast_announcement threw")
            ToolResult(call.id, false, "", e.message ?: "Announcement failed")
        }
    }

    private fun plural(n: Int) = if (n == 1) "" else "s"

    companion object {
        const val TOOL_NAME = "broadcast_announcement"
    }
}
