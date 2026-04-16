package com.opensmarthome.speaker.tool

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CompositeToolExecutorTest {

    private lateinit var executor1: ToolExecutor
    private lateinit var executor2: ToolExecutor
    private lateinit var composite: CompositeToolExecutor

    @BeforeEach
    fun setup() {
        executor1 = mockk(relaxed = true)
        executor2 = mockk(relaxed = true)

        coEvery { executor1.availableTools() } returns listOf(
            ToolSchema("tool_a", "Tool A", emptyMap()),
            ToolSchema("tool_b", "Tool B", emptyMap())
        )
        coEvery { executor2.availableTools() } returns listOf(
            ToolSchema("tool_c", "Tool C", emptyMap())
        )

        composite = CompositeToolExecutor(listOf(executor1, executor2))
    }

    @Test
    fun `availableTools aggregates from all executors`() = runTest {
        val tools = composite.availableTools()
        assertThat(tools.map { it.name }).containsExactly("tool_a", "tool_b", "tool_c")
    }

    @Test
    fun `execute routes to correct executor`() = runTest {
        val expectedResult = ToolResult("1", true, "ok")
        coEvery { executor2.execute(any()) } returns expectedResult

        composite.availableTools() // initialize routing
        val result = composite.execute(ToolCall("1", "tool_c", emptyMap()))

        assertThat(result.success).isTrue()
        coVerify { executor2.execute(any()) }
    }

    @Test
    fun `execute returns error for unknown tool`() = runTest {
        composite.availableTools()
        val result = composite.execute(ToolCall("1", "unknown", emptyMap()))

        assertThat(result.success).isFalse()
        assertThat(result.error).contains("Unknown tool")
    }

    @Test
    fun `execute refreshes tool list when tool not yet known`() = runTest {
        // Do NOT call availableTools first — this simulates the LLM firing a
        // tool call before anyone has enumerated tools. The composite should
        // self-refresh and still route correctly.
        val expected = ToolResult("1", true, "ok")
        coEvery { executor1.execute(any()) } returns expected

        val result = composite.execute(ToolCall("1", "tool_a", emptyMap()))

        assertThat(result.success).isTrue()
        assertThat(result.data).isEqualTo("ok")
    }

    @Test
    fun `recorder sees each invocation with success flag`() = runTest {
        val calls = mutableListOf<Pair<String, Boolean>>()
        val recorder = com.opensmarthome.speaker.tool.analytics.ToolUsageRecorder { name, success ->
            calls.add(name to success)
        }
        val withStats = com.opensmarthome.speaker.tool.CompositeToolExecutor(
            listOf(executor1, executor2),
            stats = recorder
        )
        coEvery { executor1.execute(any()) } returns ToolResult("1", true, "ok")
        coEvery { executor2.execute(any()) } returns ToolResult("2", false, "", "boom")

        withStats.execute(ToolCall("1", "tool_a", emptyMap()))
        withStats.execute(ToolCall("2", "tool_c", emptyMap()))

        assertThat(calls).containsExactly("tool_a" to true, "tool_c" to false).inOrder()
    }

    @Test
    fun `recorder receives unknown-tool invocations too`() = runTest {
        val calls = mutableListOf<Pair<String, Boolean>>()
        val recorder = com.opensmarthome.speaker.tool.analytics.ToolUsageRecorder { name, success ->
            calls.add(name to success)
        }
        val withStats = com.opensmarthome.speaker.tool.CompositeToolExecutor(
            listOf(executor1, executor2),
            stats = recorder
        )

        val result = withStats.execute(ToolCall("x", "never_heard_of", emptyMap()))

        assertThat(result.success).isFalse()
        assertThat(calls).containsExactly("never_heard_of" to false)
    }
}
