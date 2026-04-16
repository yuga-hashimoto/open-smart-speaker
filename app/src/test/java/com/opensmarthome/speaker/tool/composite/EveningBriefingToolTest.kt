package com.opensmarthome.speaker.tool.composite

import com.google.common.truth.Truth.assertThat
import com.opensmarthome.speaker.tool.ToolCall
import com.opensmarthome.speaker.tool.ToolExecutor
import com.opensmarthome.speaker.tool.ToolResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class EveningBriefingToolTest {

    @Test
    fun `runs notifications calendar timers by default`() = runTest {
        val inner = mockk<ToolExecutor>()
        coEvery { inner.execute(any()) } returns ToolResult("x", true, "[]")

        val r = EveningBriefingTool { inner }.execute(
            ToolCall("1", "evening_briefing", emptyMap())
        )

        assertThat(r.success).isTrue()
        coVerify(exactly = 1) { inner.execute(match { it.name == "list_notifications" }) }
        coVerify(exactly = 1) { inner.execute(match { it.name == "get_calendar_events" }) }
        coVerify(exactly = 1) { inner.execute(match { it.name == "get_timers" }) }
    }

    @Test
    fun `omits sections per flags`() = runTest {
        val inner = mockk<ToolExecutor>()
        coEvery { inner.execute(any()) } returns ToolResult("x", true, "[]")

        EveningBriefingTool { inner }.execute(
            ToolCall(
                "2", "evening_briefing",
                mapOf("include_calendar" to false, "include_timers" to false)
            )
        )

        coVerify(exactly = 1) { inner.execute(match { it.name == "list_notifications" }) }
        coVerify(exactly = 0) { inner.execute(match { it.name == "get_calendar_events" }) }
        coVerify(exactly = 0) { inner.execute(match { it.name == "get_timers" }) }
    }

    @Test
    fun `inner failure becomes null entry`() = runTest {
        val inner = mockk<ToolExecutor>()
        coEvery { inner.execute(match { it.name == "list_notifications" }) } returns
            ToolResult("a", false, "", "denied")
        coEvery { inner.execute(match { it.name == "get_calendar_events" }) } returns
            ToolResult("b", true, "[]")
        coEvery { inner.execute(match { it.name == "get_timers" }) } returns
            ToolResult("c", true, "[]")

        val r = EveningBriefingTool { inner }.execute(
            ToolCall("3", "evening_briefing", emptyMap())
        )
        assertThat(r.data).contains("\"notifications\":null")
        assertThat(r.data).contains("\"calendar\":[]")
    }

    @Test
    fun `unknown tool name fails`() = runTest {
        val inner = mockk<ToolExecutor>(relaxed = true)
        val r = EveningBriefingTool { inner }.execute(ToolCall("4", "wrong", emptyMap()))
        assertThat(r.success).isFalse()
    }
}
