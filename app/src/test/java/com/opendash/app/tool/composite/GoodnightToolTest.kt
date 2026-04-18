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

class GoodnightToolTest {

    @Test
    fun `runs lights media timers by default`() = runTest {
        val inner = mockk<ToolExecutor>()
        coEvery { inner.execute(any()) } returns ToolResult("x", true, "{}")

        GoodnightTool { inner }
            .execute(ToolCall("1", "goodnight", emptyMap()))

        coVerify(exactly = 1) {
            inner.execute(match { it.name == "execute_command" && it.arguments["device_type"] == "light" })
        }
        coVerify(exactly = 1) {
            inner.execute(match { it.name == "execute_command" && it.arguments["device_type"] == "media_player" })
        }
        coVerify(exactly = 1) { inner.execute(match { it.name == "cancel_all_timers" }) }
    }

    @Test
    fun `respects include flags`() = runTest {
        val inner = mockk<ToolExecutor>()
        coEvery { inner.execute(any()) } returns ToolResult("x", true, "{}")

        GoodnightTool { inner }.execute(
            ToolCall(
                "2", "goodnight",
                mapOf("include_media" to false, "include_timers" to false)
            )
        )

        coVerify(exactly = 1) { inner.execute(match { it.arguments["device_type"] == "light" }) }
        coVerify(exactly = 0) { inner.execute(match { it.arguments["device_type"] == "media_player" }) }
        coVerify(exactly = 0) { inner.execute(match { it.name == "cancel_all_timers" }) }
    }

    @Test
    fun `inner failure shows up as false in payload`() = runTest {
        val inner = mockk<ToolExecutor>()
        coEvery { inner.execute(match { it.arguments["device_type"] == "light" }) } returns
            ToolResult("a", false, "", "off")
        coEvery { inner.execute(match { it.arguments["device_type"] == "media_player" }) } returns
            ToolResult("b", true, "{}")
        coEvery { inner.execute(match { it.name == "cancel_all_timers" }) } returns
            ToolResult("c", true, "{}")

        val result = GoodnightTool { inner }
            .execute(ToolCall("3", "goodnight", emptyMap()))
        assertThat(result.success).isTrue()
        assertThat(result.data).contains("\"lights_off\":false")
        assertThat(result.data).contains("\"media_paused\":true")
    }

    @Test
    fun `unknown tool name returns failure`() = runTest {
        val inner = mockk<ToolExecutor>(relaxed = true)
        val result = GoodnightTool { inner }.execute(ToolCall("4", "wrong", emptyMap()))
        assertThat(result.success).isFalse()
    }
}
