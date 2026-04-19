package com.opendash.app.tool.system

import com.google.common.truth.Truth.assertThat
import com.opendash.app.tool.ToolCall
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BluetoothToolExecutorTest {

    private lateinit var executor: BluetoothToolExecutor
    private lateinit var provider: BluetoothInfoProvider

    @BeforeEach
    fun setup() {
        provider = mockk(relaxed = true)
        executor = BluetoothToolExecutor(provider)
    }

    @Test
    fun `availableTools includes list_bluetooth_devices`() = runTest {
        assertThat(executor.availableTools().map { it.name })
            .contains("list_bluetooth_devices")
    }

    @Test
    fun `list_bluetooth_devices without permission errors`() = runTest {
        every { provider.hasPermission() } returns false

        val result = executor.execute(
            ToolCall(id = "1", name = "list_bluetooth_devices", arguments = emptyMap())
        )

        assertThat(result.success).isFalse()
        assertThat(result.error).contains("BLUETOOTH_CONNECT")
    }

    @Test
    fun `list_bluetooth_devices returns devices and enabled state`() = runTest {
        every { provider.hasPermission() } returns true
        every { provider.isEnabled() } returns true
        coEvery { provider.listPairedDevices() } returns listOf(
            BluetoothDeviceInfo(
                name = "Sony Speaker",
                address = "AA:BB:CC:DD:EE:FF",
                type = "classic",
                majorClass = "audio_video",
                isConnected = false
            )
        )

        val result = executor.execute(
            ToolCall(id = "2", name = "list_bluetooth_devices", arguments = emptyMap())
        )

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("\"enabled\":true")
        assertThat(result.data).contains("Sony Speaker")
        assertThat(result.data).contains("audio_video")
        assertThat(result.data).contains("\"count\":1")
    }

    @Test
    fun `list_bluetooth_devices returns empty list when no pairings`() = runTest {
        every { provider.hasPermission() } returns true
        every { provider.isEnabled() } returns false
        coEvery { provider.listPairedDevices() } returns emptyList()

        val result = executor.execute(
            ToolCall(id = "3", name = "list_bluetooth_devices", arguments = emptyMap())
        )

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("\"enabled\":false")
        assertThat(result.data).contains("\"count\":0")
        assertThat(result.data).contains("\"devices\":[]")
    }
}
