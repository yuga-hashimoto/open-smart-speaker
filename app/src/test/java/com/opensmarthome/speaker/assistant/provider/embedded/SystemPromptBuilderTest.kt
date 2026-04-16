package com.opensmarthome.speaker.assistant.provider.embedded

import com.opensmarthome.speaker.assistant.model.AssistantMessage
import com.opensmarthome.speaker.tool.ToolParameter
import com.opensmarthome.speaker.tool.ToolSchema
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class SystemPromptBuilderTest {

    private val builder = SystemPromptBuilder()

    @Test
    fun `buildPrompt includes system prompt at the beginning`() {
        val systemPrompt = "You are a helpful assistant."
        val messages = listOf(
            AssistantMessage.User(content = "Hello")
        )

        val result = builder.build(systemPrompt, messages, emptyList())

        assertThat(result).contains(systemPrompt)
        assertThat(result.indexOf(systemPrompt)).isLessThan(result.indexOf("Hello"))
    }

    @Test
    fun `buildPrompt includes full conversation history`() {
        val messages = listOf(
            AssistantMessage.User(content = "Turn on the lights"),
            AssistantMessage.Assistant(content = "Done, lights are on."),
            AssistantMessage.User(content = "Now turn them off")
        )

        val result = builder.build("System", messages, emptyList())

        assertThat(result).contains("Turn on the lights")
        assertThat(result).contains("Done, lights are on.")
        assertThat(result).contains("Now turn them off")
    }

    @Test
    fun `buildPrompt includes tool definitions when provided`() {
        val tools = listOf(
            ToolSchema(
                name = "get_device_state",
                description = "Get device state",
                parameters = mapOf(
                    "device_id" to ToolParameter("string", "The device ID", required = true)
                )
            )
        )
        val messages = listOf(AssistantMessage.User(content = "Check the lights"))

        val result = builder.build("System", messages, tools)

        assertThat(result).contains("get_device_state")
        assertThat(result).contains("device_id")
    }

    @Test
    fun `buildPrompt includes tool call results in history`() {
        val messages = listOf(
            AssistantMessage.User(content = "Check lights"),
            AssistantMessage.Assistant(
                content = "",
                toolCalls = listOf(
                    com.opensmarthome.speaker.assistant.model.ToolCallRequest(
                        id = "call_1", name = "get_device_state", arguments = """{"device_id":"light_1"}"""
                    )
                )
            ),
            AssistantMessage.ToolCallResult(
                callId = "call_1",
                result = """{"is_on":true}"""
            ),
            AssistantMessage.User(content = "Thanks")
        )

        val result = builder.build("System", messages, emptyList())

        assertThat(result).contains("get_device_state")
        assertThat(result).contains("""{"is_on":true}""")
    }

    @Test
    fun `buildPrompt truncates history when exceeding max tokens`() {
        val longHistory = (1..100).map { i ->
            AssistantMessage.User(content = "Message number $i with some padding text to make it longer")
        }

        val result = builder.build("System", longHistory, emptyList(), maxPromptChars = 2000)

        // Should contain the system prompt and recent messages, but not all 100
        assertThat(result).contains("System")
        assertThat(result).contains("Message number 100")
        assertThat(result.length).isAtMost(2500) // some overhead for formatting
    }

    @Test
    fun `buildPrompt ends with model turn marker`() {
        val messages = listOf(AssistantMessage.User(content = "Hello"))

        val result = builder.build("System", messages, emptyList())

        assertThat(result).endsWith("<start_of_turn>model\n")
    }

    @Test
    fun `buildPrompt injects skills XML when provided`() {
        val messages = listOf(AssistantMessage.User(content = "Turn on the lights"))
        val skillsXml = """<available_skills>
  <skill>
    <name>home-control</name>
    <description>Controls smart home devices</description>
  </skill>
</available_skills>"""

        val result = builder.build("System", messages, emptyList(), skillsXml = skillsXml)

        assertThat(result).contains("<available_skills>")
        assertThat(result).contains("home-control")
        assertThat(result).contains("get_skill")
    }

    @Test
    fun `buildPrompt omits skills section when empty`() {
        val messages = listOf(AssistantMessage.User(content = "Hi"))

        val result = builder.build("System", messages, emptyList(), skillsXml = "")

        assertThat(result).doesNotContain("<available_skills>")
        assertThat(result).doesNotContain("get_skill")
    }

    @Test
    fun `buildPrompt with Qwen template uses im_start markers`() {
        val qwenBuilder = SystemPromptBuilder(template = QwenTemplate)
        val messages = listOf(AssistantMessage.User(content = "Hello"))

        val result = qwenBuilder.build("System", messages, emptyList())

        assertThat(result).contains("<|im_start|>system")
        assertThat(result).contains("<|im_start|>user")
        assertThat(result).endsWith("<|im_start|>assistant\n")
    }

    @Test
    fun `buildPrompt with Llama3 template uses header markers`() {
        val llamaBuilder = SystemPromptBuilder(template = Llama3Template)
        val messages = listOf(AssistantMessage.User(content = "Hello"))

        val result = llamaBuilder.build("System", messages, emptyList())

        assertThat(result).contains("<|start_header_id|>system")
        assertThat(result).contains("<|start_header_id|>user")
        assertThat(result).endsWith("<|start_header_id|>assistant<|end_header_id|>\n\n")
    }

    @Test
    fun `buildPrompt tool section includes JSON format instructions`() {
        val tools = listOf(
            ToolSchema(
                name = "execute_command",
                description = "Execute a command",
                parameters = mapOf(
                    "device_id" to ToolParameter("string", "Device ID", required = true),
                    "action" to ToolParameter("string", "Action", required = true)
                )
            )
        )
        val messages = listOf(AssistantMessage.User(content = "Do something"))

        val result = builder.build("System", messages, tools)

        assertThat(result).contains("tool_call")
        assertThat(result).contains("arguments")
    }
}
