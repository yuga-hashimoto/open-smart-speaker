package com.opendash.app.tool.system

import com.opendash.app.tool.ToolCall
import com.opendash.app.tool.ToolExecutor
import com.opendash.app.tool.ToolResult
import com.opendash.app.tool.ToolSchema
import timber.log.Timber

/**
 * LLM tool: report current WiFi connection state. Useful for "am I online?" /
 * "what network am I on?" questions and proactive suggestions.
 */
class WifiToolExecutor(
    private val provider: WifiInfoProvider
) : ToolExecutor {

    override suspend fun availableTools(): List<ToolSchema> = listOf(
        ToolSchema(
            name = "get_wifi_info",
            description = "Return current WiFi connection: SSID, signal strength (dBm), link speed, IPv4. Some fields may be null if the OS hides them without location/nearby-wifi permission.",
            parameters = emptyMap()
        )
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        return try {
            when (call.name) {
                "get_wifi_info" -> executeGet(call)
                else -> ToolResult(call.id, false, "", "Unknown tool: ${call.name}")
            }
        } catch (e: Exception) {
            Timber.e(e, "WiFi tool failed: ${call.name}")
            ToolResult(call.id, false, "", e.message ?: "Execution failed")
        }
    }

    private suspend fun executeGet(call: ToolCall): ToolResult {
        val enabled = provider.isEnabled()
        val s = provider.current()
        val data = """{"enabled":$enabled,"ssid":${s.ssid.toJsonOrNull()},"bssid":${s.bssid.toJsonOrNull()},"rssi_dbm":${s.rssiDbm ?: "null"},"link_speed_mbps":${s.linkSpeedMbps ?: "null"},"frequency_mhz":${s.frequencyMhz ?: "null"},"ipv4":${s.ipAddressV4.toJsonOrNull()}}"""
        return ToolResult(call.id, true, data)
    }

    private fun String?.toJsonOrNull(): String {
        val v = this
        return if (v.isNullOrEmpty()) "null" else """"${v.escapeJson()}""""
    }

    private fun String.escapeJson(): String =
        replace("\\", "\\\\").replace("\"", "\\\"")
}
