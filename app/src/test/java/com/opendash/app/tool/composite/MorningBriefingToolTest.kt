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

class MorningBriefingToolTest {

    @Test
    fun `runs all three by default`() = runTest {
        val inner = mockk<ToolExecutor>()
        coEvery { inner.execute(match { it.name == "get_weather" }) } returns
            ToolResult("w", true, """{"temp":20}""")
        coEvery { inner.execute(match { it.name == "get_news" }) } returns
            ToolResult("n", true, """[{"title":"hi"}]""")
        coEvery { inner.execute(match { it.name == "get_calendar_events" }) } returns
            ToolResult("c", true, """[]""")

        val tool = MorningBriefingTool { inner }
        val result = tool.execute(ToolCall("1", "morning_briefing", emptyMap()))

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("\"weather\":{\"temp\":20}")
        assertThat(result.data).contains("\"news\":[{\"title\":\"hi\"}]")
        assertThat(result.data).contains("\"calendar\":[]")
    }

    @Test
    fun `respects include flags`() = runTest {
        val inner = mockk<ToolExecutor>()
        coEvery { inner.execute(any()) } returns ToolResult("x", true, "{}")

        val tool = MorningBriefingTool { inner }
        tool.execute(
            ToolCall(
                "2",
                "morning_briefing",
                mapOf("include_news" to false, "include_calendar" to false)
            )
        )

        coVerify(exactly = 1) { inner.execute(match { it.name == "get_weather" }) }
        coVerify(exactly = 0) { inner.execute(match { it.name == "get_news" }) }
        coVerify(exactly = 0) { inner.execute(match { it.name == "get_calendar_events" }) }
    }

    @Test
    fun `inner failure becomes null entry`() = runTest {
        val inner = mockk<ToolExecutor>()
        coEvery { inner.execute(match { it.name == "get_weather" }) } returns
            ToolResult("w", false, "", "down")
        coEvery { inner.execute(match { it.name == "get_news" }) } returns
            ToolResult("n", true, "[]")
        coEvery { inner.execute(match { it.name == "get_calendar_events" }) } returns
            ToolResult("c", true, "[]")

        val result = MorningBriefingTool { inner }
            .execute(ToolCall("3", "morning_briefing", emptyMap()))

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("\"weather\":null")
    }
}
