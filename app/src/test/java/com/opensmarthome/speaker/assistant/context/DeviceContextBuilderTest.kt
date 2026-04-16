package com.opensmarthome.speaker.assistant.context

import com.google.common.truth.Truth.assertThat
import com.opensmarthome.speaker.device.model.Device
import com.opensmarthome.speaker.device.model.DeviceCapability
import com.opensmarthome.speaker.device.model.DeviceState
import com.opensmarthome.speaker.device.model.DeviceType
import org.junit.jupiter.api.Test

class DeviceContextBuilderTest {

    private val builder = DeviceContextBuilder()

    @Test
    fun `empty list produces empty string`() {
        assertThat(builder.build(emptyList())).isEmpty()
    }

    @Test
    fun `groups devices by room`() {
        val devices = listOf(
            device("l1", "Bedroom light", "Bedroom", DeviceType.LIGHT, isOn = true),
            device("l2", "Kitchen light", "Kitchen", DeviceType.LIGHT, isOn = false),
            device("l3", "Bedroom lamp", "Bedroom", DeviceType.LIGHT, isOn = false)
        )

        val result = builder.build(devices)

        assertThat(result).contains("<device_state>")
        assertThat(result).contains("[Bedroom]")
        assertThat(result).contains("[Kitchen]")
        assertThat(result).contains("Bedroom lamp")
        assertThat(result).contains("Kitchen light")
    }

    @Test
    fun `formats on off state`() {
        val devices = listOf(device("l1", "Lamp", "Room", DeviceType.LIGHT, isOn = true))
        val result = builder.build(devices)

        assertThat(result).contains("on")
    }

    @Test
    fun `formats temperature`() {
        val devices = listOf(
            Device(
                id = "c1", providerId = "p", name = "AC", room = "Living",
                type = DeviceType.CLIMATE, capabilities = emptySet(),
                state = DeviceState(deviceId = "c1", temperature = 22.5f)
            )
        )

        val result = builder.build(devices)
        assertThat(result).contains("temp=22.5°")
    }

    @Test
    fun `truncates with summary when over max`() {
        val devices = (1..30).map { i ->
            device("d$i", "Device$i", "Room", DeviceType.LIGHT, isOn = false)
        }

        val result = builder.build(devices)

        assertThat(result).contains("more devices not shown")
    }

    @Test
    fun `handles devices without room`() {
        val devices = listOf(
            Device(
                id = "x", providerId = "p", name = "Unknown", room = null,
                type = DeviceType.LIGHT, capabilities = emptySet(),
                state = DeviceState(deviceId = "x", isOn = true)
            )
        )

        val result = builder.build(devices)
        assertThat(result).contains("Unknown room")
    }

    private fun device(
        id: String, name: String, room: String, type: DeviceType, isOn: Boolean
    ) = Device(
        id = id, providerId = "p", name = name, room = room,
        type = type, capabilities = emptySet(),
        state = DeviceState(deviceId = id, isOn = isOn)
    )
}
