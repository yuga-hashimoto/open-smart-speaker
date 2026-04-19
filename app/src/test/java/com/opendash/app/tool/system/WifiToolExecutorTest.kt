package com.opendash.app.tool.system

import com.google.common.truth.Truth.assertThat
import com.opendash.app.tool.ToolCall
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class WifiToolExecutorTest {

    private lateinit var executor: WifiToolExecutor
    private lateinit var provider: WifiInfoProvider

    @BeforeEach
    fun setup() {
        provider = mockk(relaxed = true)
        executor = WifiToolExecutor(provider)
    }

    @Test
    fun `availableTools includes get_wifi_info`() = runTest {
        assertThat(executor.availableTools().map { it.name })
            .contains("get_wifi_info")
    }

    @Test
    fun `get_wifi_info returns full snapshot`() = runTest {
        every { provider.isEnabled() } returns true
        coEvery { provider.current() } returns WifiSnapshot(
            ssid = "MyHomeWifi",
            bssid = "11:22:33:44:55:66",
            rssiDbm = -55,
            linkSpeedMbps = 433,
            frequencyMhz = 5180,
            ipAddressV4 = "192.168.1.42"
        )

        val result = executor.execute(
            ToolCall(id = "1", name = "get_wifi_info", arguments = emptyMap())
        )

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("\"enabled\":true")
        assertThat(result.data).contains("MyHomeWifi")
        assertThat(result.data).contains("\"rssi_dbm\":-55")
        assertThat(result.data).contains("\"link_speed_mbps\":433")
        assertThat(result.data).contains("192.168.1.42")
    }

    @Test
    fun `get_wifi_info handles all-null snapshot`() = runTest {
        every { provider.isEnabled() } returns false
        coEvery { provider.current() } returns WifiSnapshot(null, null, null, null, null, null)

        val result = executor.execute(
            ToolCall(id = "2", name = "get_wifi_info", arguments = emptyMap())
        )

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("\"ssid\":null")
        assertThat(result.data).contains("\"rssi_dbm\":null")
        assertThat(result.data).contains("\"ipv4\":null")
    }
}
