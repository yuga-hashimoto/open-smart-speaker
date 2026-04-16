package com.opensmarthome.speaker.tool.system

import com.google.common.truth.Truth.assertThat
import com.opensmarthome.speaker.tool.ToolCall
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DeviceHealthToolExecutorTest {

    private lateinit var provider: DeviceHealthProvider
    private lateinit var executor: DeviceHealthToolExecutor

    @BeforeEach
    fun setup() {
        provider = mockk()
        executor = DeviceHealthToolExecutor(provider)
    }

    @Test
    fun `availableTools has get_device_health`() = runTest {
        assertThat(executor.availableTools().map { it.name }).containsExactly("get_device_health")
    }

    @Test
    fun `get_device_health returns formatted snapshot`() = runTest {
        coEvery { provider.snapshot() } returns DeviceHealth(
            batteryPercent = 85,
            isCharging = true,
            batteryTemperatureC = 32.5f,
            totalRamMb = 8192,
            availableRamMb = 3500,
            totalStorageMb = 131072,
            availableStorageMb = 50000,
            model = "Pixel 9",
            androidVersion = "14"
        )

        val result = executor.execute(
            ToolCall("1", "get_device_health", emptyMap())
        )

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("\"battery_percent\":85")
        assertThat(result.data).contains("\"is_charging\":true")
        assertThat(result.data).contains("32.5")
        assertThat(result.data).contains("Pixel 9")
    }

    @Test
    fun `null battery is serialized as null`() = runTest {
        coEvery { provider.snapshot() } returns DeviceHealth(
            batteryPercent = null,
            isCharging = false,
            batteryTemperatureC = null,
            totalRamMb = 0, availableRamMb = 0,
            totalStorageMb = 0, availableStorageMb = 0,
            model = "emu", androidVersion = "14"
        )

        val result = executor.execute(
            ToolCall("2", "get_device_health", emptyMap())
        )

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("\"battery_percent\":null")
        assertThat(result.data).contains("\"battery_temp_c\":null")
    }

    @Test
    fun `unknown tool returns error`() = runTest {
        val result = executor.execute(
            ToolCall("3", "unknown", emptyMap())
        )

        assertThat(result.success).isFalse()
    }
}
