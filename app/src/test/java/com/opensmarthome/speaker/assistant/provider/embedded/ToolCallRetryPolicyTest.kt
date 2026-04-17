package com.opensmarthome.speaker.assistant.provider.embedded

import com.google.common.truth.Truth.assertThat
import com.opensmarthome.speaker.tool.ToolSchema
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class ToolCallRetryPolicyTest {

    private val policy = ToolCallRetryPolicy()

    private val tools = listOf(
        ToolSchema("web_search", "Search the web", emptyMap()),
        ToolSchema("get_weather", "Weather", emptyMap())
    )

    @Test
    fun `first attempt tool call returns without retry`() = runTest {
        var retries = 0
        val first = """{"tool_call": {"name": "get_weather", "arguments": {"location": "Tokyo"}}}"""

        val result = policy.finalize(first, tools) {
            retries++
            "should not run"
        }

        assertThat(retries).isEqualTo(0)
        assertThat(result.toolCalls).hasSize(1)
        assertThat(result.toolCalls[0].name).isEqualTo("get_weather")
    }

    @Test
    fun `plain answer returns without retry`() = runTest {
        var retries = 0
        val first = "The capital of France is Paris."

        val result = policy.finalize(first, tools) {
            retries++
            "should not run"
        }

        assertThat(retries).isEqualTo(0)
        assertThat(result.toolCalls).isEmpty()
        assertThat(result.content).contains("Paris")
    }

    @Test
    fun `refusal in english triggers retry that recovers with tool call`() = runTest {
        var retries = 0
        val first = "I'm sorry, I don't have tools to help with that."

        val result = policy.finalize(first, tools) {
            retries++
            """{"tool_call": {"name": "web_search", "arguments": {"query": "x"}}}"""
        }

        assertThat(retries).isEqualTo(1)
        assertThat(result.toolCalls).hasSize(1)
        assertThat(result.toolCalls[0].name).isEqualTo("web_search")
    }

    @Test
    fun `refusal in japanese triggers retry`() = runTest {
        var retries = 0
        val first = "申し訳ありませんが、私はツールを持っていません。"

        val result = policy.finalize(first, tools) {
            retries++
            """TOOL_CALL: get_weather(location="東京")"""
        }

        assertThat(retries).isEqualTo(1)
        assertThat(result.toolCalls).hasSize(1)
        assertThat(result.toolCalls[0].name).isEqualTo("get_weather")
    }

    @Test
    fun `retry that still refuses returns refusal text without another retry`() = runTest {
        var retries = 0
        val first = "できません。"

        val result = policy.finalize(first, tools) {
            retries++
            "I can't do that either."
        }

        assertThat(retries).isEqualTo(1) // only one retry
        assertThat(result.toolCalls).isEmpty()
        assertThat(result.content).isNotEmpty()
    }

    @Test
    fun `no retry when tool list is empty even on refusal`() = runTest {
        var retries = 0
        val first = "I don't have tools."

        val result = policy.finalize(first, tools = emptyList()) {
            retries++
            "never"
        }

        assertThat(retries).isEqualTo(0)
        assertThat(result.toolCalls).isEmpty()
    }
}
