package com.opensmarthome.speaker.assistant.provider.embedded

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ToolCallParserTest {

    private val parser = ToolCallParser()

    @Test
    fun `parse plain text response returns text only`() {
        val result = parser.parse("The lights are already on.")

        assertThat(result.text).isEqualTo("The lights are already on.")
        assertThat(result.toolCalls).isEmpty()
    }

    @Test
    fun `parse tool_call JSON extracts name and arguments`() {
        val response = """{"tool_call": {"name": "get_device_state", "arguments": {"device_id": "light_1"}}}"""

        val result = parser.parse(response)

        assertThat(result.toolCalls).hasSize(1)
        assertThat(result.toolCalls[0].name).isEqualTo("get_device_state")
        assertThat(result.toolCalls[0].arguments).contains("light_1")
    }

    @Test
    fun `parse tool_call with surrounding text extracts both`() {
        val response = """Let me check that for you.
{"tool_call": {"name": "get_device_state", "arguments": {"device_id": "light_1"}}}"""

        val result = parser.parse(response)

        assertThat(result.text).contains("Let me check that for you")
        assertThat(result.toolCalls).hasSize(1)
        assertThat(result.toolCalls[0].name).isEqualTo("get_device_state")
    }

    @Test
    fun `parse legacy tool format for backward compatibility`() {
        val response = """{"tool": "execute_command", "arguments": {"device_id": "switch_1", "action": "turn_on"}}"""

        val result = parser.parse(response)

        assertThat(result.toolCalls).hasSize(1)
        assertThat(result.toolCalls[0].name).isEqualTo("execute_command")
    }

    @Test
    fun `parse multiple tool calls in single response`() {
        val response = """I'll check both devices.
{"tool_call": {"name": "get_device_state", "arguments": {"device_id": "light_1"}}}
{"tool_call": {"name": "get_device_state", "arguments": {"device_id": "light_2"}}}"""

        val result = parser.parse(response)

        assertThat(result.toolCalls).hasSize(2)
        assertThat(result.toolCalls[0].arguments).contains("light_1")
        assertThat(result.toolCalls[1].arguments).contains("light_2")
    }

    @Test
    fun `parse malformed JSON returns text only`() {
        val response = """{"tool_call": {"name": "bad_json", arguments: broken}}"""

        val result = parser.parse(response)

        assertThat(result.toolCalls).isEmpty()
        assertThat(result.text).isNotEmpty()
    }

    @Test
    fun `parse empty response returns empty text`() {
        val result = parser.parse("")

        assertThat(result.text).isEmpty()
        assertThat(result.toolCalls).isEmpty()
    }

    @Test
    fun `parse tool_call with nested arguments`() {
        val response = """{"tool_call": {"name": "execute_command", "arguments": {"device_id": "light_1", "action": "set_brightness", "parameters": {"brightness": 50}}}}"""

        val result = parser.parse(response)

        assertThat(result.toolCalls).hasSize(1)
        assertThat(result.toolCalls[0].name).isEqualTo("execute_command")
        assertThat(result.toolCalls[0].arguments).contains("brightness")
    }
}
