package com.opensmarthome.speaker.assistant.provider.embedded

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.opensmarthome.speaker.assistant.model.AssistantMessage
import com.opensmarthome.speaker.assistant.model.ToolCallRequest
import com.opensmarthome.speaker.tool.ToolSchema
import io.mockk.mockk
import java.io.File
import org.junit.jupiter.api.Test

/**
 * Verifies that [EmbeddedLlmProvider.buildEnrichedPrompt] emits a
 * "summarized tool result + answer directive" block when the most recent
 * message in conversation history is an [AssistantMessage.ToolCallResult].
 *
 * This is the fix for the agent-loop 2nd round "..." bug (PR #418):
 *
 *   Round 1: user "Tell me about X" → LLM emits tool call → ToolExecutor
 *     runs → VoicePipeline appends `ToolCallResult(raw JSON)` to history.
 *   Round 2: provider.send(..., history) is called again. The original
 *     code extracted only the last `User` message for the prompt, which
 *     meant the LLM never saw the tool result and emitted "..." because
 *     it thought the tool call was still pending.
 *
 *   Fix: when the last message is a tool result, build the prompt as
 *     "User asked: …\n[Tool Result: …] <summary>\n<answer directive>".
 */
class EmbeddedLlmProviderToolResultSummaryTest {

    private val mockContext = mockk<Context>(relaxed = true)

    private val tools = listOf(
        ToolSchema("web_search", "Search the web", emptyMap()),
        ToolSchema("get_weather", "Weather", emptyMap())
    )

    private fun provider() = EmbeddedLlmProvider(
        context = mockContext,
        config = EmbeddedLlmConfig(modelPath = File.createTempFile("embedded-llm", ".bin").absolutePath)
    )

    @Test
    fun `first round emits user question at the end`() {
        val messages = listOf(
            AssistantMessage.User(content = "What is the weather in Tokyo?")
        )

        val prompt = provider().buildEnrichedPrompt(messages, tools, retry = false)

        assertThat(prompt).contains("What is the weather in Tokyo?")
        assertThat(prompt).doesNotContain("[Tool Result:")
    }

    @Test
    fun `second round emits summarized tool result with answer directive`() {
        val callId = "call_123"
        val rawWebSearch = """{"query":"Webb telescope","abstract":"The James Webb Space Telescope is an observatory.","source_url":null,"related":["infrared astronomy"]}"""

        val messages = listOf(
            AssistantMessage.User(content = "Tell me about the Webb telescope."),
            AssistantMessage.Assistant(
                content = "",
                toolCalls = listOf(
                    ToolCallRequest(
                        id = callId,
                        name = "web_search",
                        arguments = """{"query":"Webb telescope"}"""
                    )
                )
            ),
            AssistantMessage.ToolCallResult(
                callId = callId,
                result = rawWebSearch
            )
        )

        val prompt = provider().buildEnrichedPrompt(messages, tools, retry = false)

        // Tool result is summarized (FastPathResultFormatter applies "Abstract:" prefix).
        assertThat(prompt).contains("Abstract:")
        assertThat(prompt).contains("James Webb Space Telescope")
        // The tool name is carried through.
        assertThat(prompt).contains("[Tool Result: web_search]")
        // The original user question is re-stated so the model has context.
        assertThat(prompt).contains("Webb telescope")
        // The answer directive tells Gemma to answer in 1-2 sentences and
        // forbids the degenerate "..." output.
        assertThat(prompt).contains(EmbeddedLlmProvider.TOOL_RESULT_ANSWER_DIRECTIVE)
    }

    @Test
    fun `second round uses the tool's real name when available`() {
        val callId = "call_w"
        val rawWeather =
            """{"location":"Tokyo","temperature_c":18,"condition":"Clear","humidity":65}"""

        val messages = listOf(
            AssistantMessage.User(content = "Weather in Tokyo?"),
            AssistantMessage.Assistant(
                content = "",
                toolCalls = listOf(
                    ToolCallRequest(id = callId, name = "get_weather", arguments = "{}")
                )
            ),
            AssistantMessage.ToolCallResult(callId = callId, result = rawWeather)
        )

        val prompt = provider().buildEnrichedPrompt(messages, tools, retry = false)

        assertThat(prompt).contains("[Tool Result: get_weather]")
        assertThat(prompt).contains("Current:")
        assertThat(prompt).contains("Tokyo")
    }

    @Test
    fun `second round caps tool result summary length`() {
        val callId = "call_big"
        val huge = "x".repeat(10_000)
        val messages = listOf(
            AssistantMessage.User(content = "Search"),
            AssistantMessage.Assistant(
                content = "",
                toolCalls = listOf(
                    ToolCallRequest(id = callId, name = "unknown_tool", arguments = "{}")
                )
            ),
            AssistantMessage.ToolCallResult(callId = callId, result = huge)
        )

        val prompt = provider().buildEnrichedPrompt(messages, tools, retry = false)

        // Not bounded exactly, but far below 10k (summary cap is 800).
        assertThat(prompt.length).isLessThan(10_000)
    }
}
