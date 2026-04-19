package com.opendash.app.tool.system

import com.opendash.app.tool.ToolCall
import com.opendash.app.tool.ToolExecutor
import com.opendash.app.tool.ToolResult
import com.opendash.app.tool.ToolSchema
import timber.log.Timber

/**
 * LLM tool: list Bluetooth devices that are paired with the tablet.
 * Useful for "what speakers can I play music on?" / "is my watch connected?".
 */
class BluetoothToolExecutor(
    private val provider: BluetoothInfoProvider
) : ToolExecutor {

    override suspend fun availableTools(): List<ToolSchema> = listOf(
        ToolSchema(
            name = "list_bluetooth_devices",
            description = "Return all Bluetooth devices currently paired with this tablet, plus whether the radio is on.",
            parameters = emptyMap()
        )
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        return try {
            when (call.name) {
                "list_bluetooth_devices" -> executeList(call)
                else -> ToolResult(call.id, false, "", "Unknown tool: ${call.name}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Bluetooth tool failed: ${call.name}")
            ToolResult(call.id, false, "", e.message ?: "Execution failed")
        }
    }

    private suspend fun executeList(call: ToolCall): ToolResult {
        if (!provider.hasPermission()) {
            return ToolResult(
                call.id, false, "",
                "Bluetooth permission not granted. Ask user to grant BLUETOOTH_CONNECT."
            )
        }
        val enabled = provider.isEnabled()
        val devices = provider.listPairedDevices()
        val items = devices.joinToString(",") { d ->
            """{"name":"${d.name.escapeJson()}","address":"${d.address.escapeJson()}","type":"${d.type}","class":"${d.majorClass}"}"""
        }
        return ToolResult(
            call.id, true,
            """{"enabled":$enabled,"count":${devices.size},"devices":[$items]}"""
        )
    }

    private fun String.escapeJson(): String =
        replace("\\", "\\\\").replace("\"", "\\\"")
}
