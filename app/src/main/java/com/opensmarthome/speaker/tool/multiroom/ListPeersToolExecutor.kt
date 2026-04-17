package com.opensmarthome.speaker.tool.multiroom

import com.opensmarthome.speaker.tool.ToolCall
import com.opensmarthome.speaker.tool.ToolExecutor
import com.opensmarthome.speaker.tool.ToolResult
import com.opensmarthome.speaker.tool.ToolSchema
import com.opensmarthome.speaker.util.MulticastDiscovery

/**
 * `list_peers` tool — surfaces the current mDNS peer list so the user can
 * audit their multi-room mesh via voice ("who's on the network?").
 *
 * Pure read path: reads [MulticastDiscovery.speakers] without starting
 * discovery (the VoiceService already does that when multi-room is on).
 * Returns an empty list when multi-room isn't enabled — no mistake message,
 * the LLM or fast-path matcher can decide how to narrate zero peers.
 */
class ListPeersToolExecutor(
    private val discovery: MulticastDiscovery
) : ToolExecutor {

    override suspend fun availableTools(): List<ToolSchema> = listOf(
        ToolSchema(
            name = "list_peers",
            description = "List OpenSmartSpeaker peers discovered on the LAN via mDNS. " +
                "Returns each peer's service name and, when resolved, host + port.",
            parameters = emptyMap()
        )
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        if (call.name != "list_peers") {
            return ToolResult(call.id, false, "", "Unknown tool: ${call.name}")
        }
        val peers = discovery.speakers.value
        val blob = buildString {
            append("{\"count\":")
            append(peers.size)
            append(",\"peers\":[")
            peers.forEachIndexed { idx, peer ->
                if (idx > 0) append(',')
                append('{')
                append("\"name\":\"").append(escape(peer.serviceName)).append('"')
                peer.host?.let { append(",\"host\":\"").append(escape(it)).append('"') }
                peer.port?.let { append(",\"port\":").append(it) }
                append('}')
            }
            append("]}")
        }
        return ToolResult(call.id, true, blob)
    }

    private fun escape(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")
}
