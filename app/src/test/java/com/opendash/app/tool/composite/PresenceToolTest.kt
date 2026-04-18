package com.opendash.app.tool.composite

import com.google.common.truth.Truth.assertThat
import com.opendash.app.tool.ToolCall
import com.opendash.app.tool.ToolExecutor
import com.opendash.app.tool.ToolResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class PresenceToolTest {

    @Test
    fun `arrive_home turns on lights and sets volume`() = runTest {
        val inner = mockk<ToolExecutor>()
        coEvery { inner.execute(any()) } returns ToolResult("x", true, "{}")

        val tool = PresenceTool { inner }
        val r = tool.execute(ToolCall("1", "arrive_home", emptyMap()))

        assertThat(r.success).isTrue()
        coVerify {
            inner.execute(match { it.arguments["device_type"] == "light" && it.arguments["action"] == "turn_on" })
        }
        coVerify { inner.execute(match { it.name == "set_volume" }) }
    }

    @Test
    fun `leave_home turns off lights and pauses media`() = runTest {
        val inner = mockk<ToolExecutor>()
        coEvery { inner.execute(any()) } returns ToolResult("x", true, "{}")

        PresenceTool { inner }.execute(ToolCall("2", "leave_home", emptyMap()))

        coVerify {
            inner.execute(match { it.arguments["device_type"] == "light" && it.arguments["action"] == "turn_off" })
        }
        coVerify {
            inner.execute(match { it.arguments["device_type"] == "media_player" && it.arguments["action"] == "media_pause" })
        }
    }

    @Test
    fun `unknown tool name returns failure`() = runTest {
        val inner = mockk<ToolExecutor>(relaxed = true)
        val r = PresenceTool { inner }.execute(ToolCall("3", "wrong", emptyMap()))
        assertThat(r.success).isFalse()
    }
}
