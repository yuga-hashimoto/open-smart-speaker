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

    // --- Bug B: leaked Gemma role markers must never reach TTS ---

    @Test
    fun `leaked User role marker is stripped from plain answer`() = runTest {
        val first = "User..."

        val result = policy.finalize(first, tools) { "never" }

        assertThat(result.toolCalls).isEmpty()
        // After stripping the bare "User..." role-marker leak, content must
        // not contain "User" — otherwise TTS announces "User..." to the user.
        assertThat(result.content).doesNotContain("User")
    }

    @Test
    fun `leaked User colon prefix keeps body but drops marker`() = runTest {
        val first = "User: Hello there"

        val result = policy.finalize(first, tools) { "never" }

        assertThat(result.content).isEqualTo("Hello there")
    }

    @Test
    fun `leaked assistant marker is stripped`() = runTest {
        val first = "<|assistant|>\nThe weather is sunny."

        val result = policy.finalize(first, tools) { "never" }

        assertThat(result.content).isEqualTo("The weather is sunny.")
    }

    // --- Expanded refusal / "I don't know" detection ---

    @Test
    fun `english I don't know triggers retry`() = runTest {
        var retries = 0
        val first = "I don't know about that topic."

        val result = policy.finalize(first, tools) {
            retries++
            """{"tool_call": {"name": "web_search", "arguments": {"query": "topic"}}}"""
        }

        assertThat(retries).isEqualTo(1)
        assertThat(result.toolCalls).hasSize(1)
    }

    @Test
    fun `japanese wakarimasen triggers retry`() = runTest {
        var retries = 0
        val first = "わかりません。"

        val result = policy.finalize(first, tools) {
            retries++
            """{"tool_call": {"name": "web_search", "arguments": {"query": "x"}}}"""
        }

        assertThat(retries).isEqualTo(1)
        assertThat(result.toolCalls).hasSize(1)
    }

    @Test
    fun `japanese moushiwake triggers retry`() = runTest {
        var retries = 0
        val first = "申し訳ございませんが、お答えできません。"

        val result = policy.finalize(first, tools) {
            retries++
            """{"tool_call": {"name": "web_search", "arguments": {"query": "x"}}}"""
        }

        assertThat(retries).isEqualTo(1)
        assertThat(result.toolCalls).hasSize(1)
    }

    // --- Empty / degenerate output detection (agent-loop 2nd-round "..." bug) ---

    @Test
    fun `bare ascii ellipsis triggers retry`() = runTest {
        var retries = 0
        val first = "..."

        val result = policy.finalize(first, tools) {
            retries++
            "Tokyo is currently 18 degrees and clear."
        }

        assertThat(retries).isEqualTo(1)
        assertThat(result.content).contains("Tokyo")
    }

    @Test
    fun `unicode horizontal ellipsis triggers retry`() = runTest {
        var retries = 0
        val first = "…"

        val result = policy.finalize(first, tools) {
            retries++
            "Here is a useful answer."
        }

        assertThat(retries).isEqualTo(1)
        assertThat(result.content).contains("useful answer")
    }

    @Test
    fun `whitespace-only response triggers retry`() = runTest {
        var retries = 0
        val first = "   \n   "

        val result = policy.finalize(first, tools) {
            retries++
            "Concrete answer."
        }

        assertThat(retries).isEqualTo(1)
        assertThat(result.content).contains("Concrete")
    }

    @Test
    fun `short gibberish under ten chars triggers retry`() = runTest {
        var retries = 0
        val first = "...\n..."

        val result = policy.finalize(first, tools) {
            retries++
            "Real answer with enough characters."
        }

        assertThat(retries).isEqualTo(1)
        assertThat(result.content).contains("Real answer")
    }

    @Test
    fun `empty retry does not trigger another retry`() = runTest {
        var retries = 0
        val first = "..."

        val result = policy.finalize(first, tools) {
            retries++
            "..." // retry also empty
        }

        assertThat(retries).isEqualTo(1) // exactly one retry
        // Fallback content must be non-blank so the user hears SOMETHING rather
        // than silence after a double-"..." degenerate model pass.
        assertThat(result.content).isNotEmpty()
    }

    @Test
    fun `short real answer is not flagged as empty`() = runTest {
        // "Yes." / "はい。" etc. are legitimate short answers and must NOT
        // trigger a retry.
        var retries = 0
        val first = "はい、そうです。"

        val result = policy.finalize(first, tools) {
            retries++
            "never"
        }

        assertThat(retries).isEqualTo(0)
        assertThat(result.content).isEqualTo("はい、そうです。")
    }
}
