package com.opendash.app.assistant.context

import com.opendash.app.device.model.Device

/**
 * Builds a compact human-readable snapshot of current device states
 * suitable for injecting into the system prompt.
 *
 * This gives the LLM situational awareness: it can see which lights
 * are on, what temperature the climate is set to, etc., without
 * having to call a tool first.
 */
class DeviceContextBuilder(
    private val maxDevices: Int = 25
) {

    fun build(devices: Collection<Device>): String {
        if (devices.isEmpty()) return ""

        // Group by room for readability
        val grouped = devices
            .take(maxDevices)
            .groupBy { it.room ?: "Unknown room" }
            .toSortedMap()

        val sb = StringBuilder()
        sb.append("<device_state>")
        for ((room, roomDevices) in grouped) {
            sb.append("\n  [${room}]")
            for (device in roomDevices.sortedBy { it.name }) {
                sb.append("\n    - ${device.name} (${device.type.name.lowercase()}): ")
                sb.append(formatState(device))
            }
        }
        if (devices.size > maxDevices) {
            sb.append("\n  ...(${devices.size - maxDevices} more devices not shown)")
        }
        sb.append("\n</device_state>")
        return sb.toString()
    }

    private fun formatState(device: Device): String {
        val state = device.state
        val parts = mutableListOf<String>()

        state.isOn?.let { parts.add(if (it) "on" else "off") }
        state.brightness?.let { parts.add("brightness=${it.toInt()}") }
        state.temperature?.let { parts.add("temp=${"%.1f".format(it)}°") }
        state.humidity?.let { parts.add("humidity=${it.toInt()}%") }
        state.mediaTitle?.takeIf { it.isNotBlank() }?.let { parts.add("playing=\"$it\"") }

        return if (parts.isEmpty()) "state unknown" else parts.joinToString(", ")
    }
}
