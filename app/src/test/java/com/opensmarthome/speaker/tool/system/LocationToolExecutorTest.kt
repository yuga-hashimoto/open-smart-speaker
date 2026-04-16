package com.opensmarthome.speaker.tool.system

import com.google.common.truth.Truth.assertThat
import com.opensmarthome.speaker.tool.ToolCall
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LocationToolExecutorTest {

    private lateinit var executor: LocationToolExecutor
    private lateinit var provider: LocationProvider

    @BeforeEach
    fun setup() {
        provider = mockk(relaxed = true)
        executor = LocationToolExecutor(provider)
    }

    @Test
    fun `availableTools includes get_location`() = runTest {
        val tools = executor.availableTools()
        assertThat(tools.map { it.name }).contains("get_location")
    }

    @Test
    fun `get_location without permission returns error`() = runTest {
        every { provider.hasPermission() } returns false

        val result = executor.execute(
            ToolCall(id = "1", name = "get_location", arguments = emptyMap())
        )

        assertThat(result.success).isFalse()
        assertThat(result.error).contains("permission")
    }

    @Test
    fun `get_location returns coordinates`() = runTest {
        every { provider.hasPermission() } returns true
        coEvery { provider.getCurrent(LocationProvider.Accuracy.BALANCED) } returns LocationResult(
            latitude = 35.6762,
            longitude = 139.6503,
            accuracyMeters = 10.5f,
            timestampMs = 1700000000000L,
            provider = "network"
        )

        val result = executor.execute(
            ToolCall(id = "2", name = "get_location", arguments = emptyMap())
        )

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("35.6762")
        assertThat(result.data).contains("139.6503")
        assertThat(result.data).contains("network")
    }

    @Test
    fun `precise accuracy is passed through`() = runTest {
        every { provider.hasPermission() } returns true
        coEvery { provider.getCurrent(LocationProvider.Accuracy.PRECISE) } returns LocationResult(
            latitude = 0.0, longitude = 0.0, accuracyMeters = 5f,
            timestampMs = 0L, provider = "gps"
        )

        val result = executor.execute(
            ToolCall(id = "3", name = "get_location", arguments = mapOf("accuracy" to "precise"))
        )

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("gps")
    }

    @Test
    fun `null result returns error`() = runTest {
        every { provider.hasPermission() } returns true
        coEvery { provider.getCurrent(any()) } returns null

        val result = executor.execute(
            ToolCall(id = "4", name = "get_location", arguments = emptyMap())
        )

        assertThat(result.success).isFalse()
        assertThat(result.error).contains("Unable to get location")
    }
}
