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

    @Test
    fun `parse XML wrapper with bare JSON inside`() {
        val response = """Looking up the weather.
<tool_call>{"name": "get_weather", "arguments": {"location": "Tokyo"}}</tool_call>"""

        val result = parser.parse(response)

        assertThat(result.toolCalls).hasSize(1)
        assertThat(result.toolCalls[0].name).isEqualTo("get_weather")
        assertThat(result.toolCalls[0].arguments).contains("Tokyo")
        assertThat(result.text).contains("Looking up the weather")
        assertThat(result.text).doesNotContain("tool_call")
    }

    @Test
    fun `parse Gemma 4 style XML tokens`() {
        val response = """<|tool_call>{"name": "set_timer", "arguments": {"seconds": 60}}<tool_call|>"""

        val result = parser.parse(response)

        assertThat(result.toolCalls).hasSize(1)
        assertThat(result.toolCalls[0].name).isEqualTo("set_timer")
        assertThat(result.text).isEmpty()
    }

    @Test
    fun `parse XML wrapper spanning multiple lines`() {
        val response = """<tool_call>
{
  "name": "execute_command",
  "arguments": {
    "device_id": "light_1"
  }
}
</tool_call>"""

        val result = parser.parse(response)

        assertThat(result.toolCalls).hasSize(1)
        assertThat(result.toolCalls[0].name).isEqualTo("execute_command")
    }

    @Test
    fun `mixed XML and JSON formats both parsed`() {
        val response = """<tool_call>{"name": "get_weather", "arguments": {"location": "NY"}}</tool_call>
Then also:
{"tool_call": {"name": "get_datetime", "arguments": {}}}"""

        val result = parser.parse(response)

        assertThat(result.toolCalls).hasSize(2)
        val names = result.toolCalls.map { it.name }
        assertThat(names).containsExactly("get_weather", "get_datetime").inOrder()
    }

    // --- Tolerant parsing: new formats for on-device LLM reliability ---

    @Test
    fun `parse single-hierarchy JSON without tool_call wrapper`() {
        val response = """{"name": "web_search", "arguments": {"query": "tokyo weather"}}"""

        val result = parser.parse(response)

        assertThat(result.toolCalls).hasSize(1)
        assertThat(result.toolCalls[0].name).isEqualTo("web_search")
        assertThat(result.toolCalls[0].arguments).contains("tokyo weather")
    }

    @Test
    fun `parse single-hierarchy JSON with leading text`() {
        val response = """Let me search.
{"name": "get_news", "arguments": {"topic": "AI"}}"""

        val result = parser.parse(response)

        assertThat(result.toolCalls).hasSize(1)
        assertThat(result.toolCalls[0].name).isEqualTo("get_news")
        assertThat(result.text).contains("Let me search")
    }

    @Test
    fun `parse natural language TOOL_CALL with string arg`() {
        val response = """TOOL_CALL: web_search(query="tokyo weather")"""

        val result = parser.parse(response)

        assertThat(result.toolCalls).hasSize(1)
        assertThat(result.toolCalls[0].name).isEqualTo("web_search")
        assertThat(result.toolCalls[0].arguments).contains("tokyo weather")
        assertThat(result.toolCalls[0].arguments).contains("query")
    }

    @Test
    fun `parse natural language TOOL_CALL with numeric and string args`() {
        val response = """TOOL_CALL: set_timer(seconds=60, label="pasta")"""

        val result = parser.parse(response)

        assertThat(result.toolCalls).hasSize(1)
        assertThat(result.toolCalls[0].name).isEqualTo("set_timer")
        assertThat(result.toolCalls[0].arguments).contains("60")
        assertThat(result.toolCalls[0].arguments).contains("pasta")
    }

    @Test
    fun `parse natural language TOOL_CALL with no args`() {
        val response = """TOOL_CALL: get_datetime()"""

        val result = parser.parse(response)

        assertThat(result.toolCalls).hasSize(1)
        assertThat(result.toolCalls[0].name).isEqualTo("get_datetime")
        assertThat(result.toolCalls[0].arguments).isEqualTo("{}")
    }

    @Test
    fun `parse natural language TOOL_CALL with surrounding text`() {
        val response = """Sure, let me check.
TOOL_CALL: get_weather(location="Tokyo")
I'll tell you shortly."""

        val result = parser.parse(response)

        assertThat(result.toolCalls).hasSize(1)
        assertThat(result.toolCalls[0].name).isEqualTo("get_weather")
        assertThat(result.text).contains("Sure, let me check")
        assertThat(result.text).doesNotContain("TOOL_CALL")
    }

    @Test
    fun `parse tolerates markdown code fences around JSON`() {
        val response = """```json
{"tool_call": {"name": "get_weather", "arguments": {"location": "Tokyo"}}}
```"""

        val result = parser.parse(response)

        assertThat(result.toolCalls).hasSize(1)
        assertThat(result.toolCalls[0].name).isEqualTo("get_weather")
    }

    // --- Hybrid-tag format: Gemma 4 E2B's invented `<|tool_call>call:X{...}` shape ---

    @Test
    fun `parse hybrid tag with call prefix and JSON-lite args`() {
        // Real log emission from Gemma 4 E2B on-device.
        val response = """<|tool_call>call:web_search{query: "Tokyo weather"}<tool_call|>"""

        val result = parser.parse(response)

        assertThat(result.toolCalls).hasSize(1)
        assertThat(result.toolCalls[0].name).isEqualTo("web_search")
        assertThat(result.toolCalls[0].arguments).contains("\"query\"")
        assertThat(result.toolCalls[0].arguments).contains("Tokyo weather")
        assertThat(result.text).isEmpty()
    }

    @Test
    fun `parse hybrid tag tolerates truncated closing tag`() {
        // Real log — model cut off mid-closing.
        val response = """<|tool_call>call:web_search{query: "トマト ウェブ"}<tool..."""

        val result = parser.parse(response)

        assertThat(result.toolCalls).hasSize(1)
        assertThat(result.toolCalls[0].name).isEqualTo("web_search")
        assertThat(result.toolCalls[0].arguments).contains("トマト ウェブ")
    }

    @Test
    fun `parse hybrid tag with parenthesis args and equals syntax`() {
        val response = """<tool_call>web_search(query="Python")</tool_call>"""

        val result = parser.parse(response)

        assertThat(result.toolCalls).hasSize(1)
        assertThat(result.toolCalls[0].name).isEqualTo("web_search")
        assertThat(result.toolCalls[0].arguments).contains("Python")
    }

    @Test
    fun `parse hybrid tag without pipe delimiters`() {
        val response = """<tool_call>call:get_weather{location: "Tokyo"}</tool_call>"""

        val result = parser.parse(response)

        assertThat(result.toolCalls).hasSize(1)
        assertThat(result.toolCalls[0].name).isEqualTo("get_weather")
        assertThat(result.toolCalls[0].arguments).contains("Tokyo")
    }

    @Test
    fun `parse hybrid tag with multiple key-value pairs`() {
        val response = """<|tool_call>call:set_timer{seconds: 60, label: "pasta"}<tool_call|>"""

        val result = parser.parse(response)

        assertThat(result.toolCalls).hasSize(1)
        assertThat(result.toolCalls[0].name).isEqualTo("set_timer")
        assertThat(result.toolCalls[0].arguments).contains("60")
        assertThat(result.toolCalls[0].arguments).contains("pasta")
    }

    @Test
    fun `parse hybrid tag does not break existing JSON-in-XML parsing`() {
        // Canonical JSON-in-XML should still work — hybrid regex requires a
        // letter before the opening bracket, so it doesn't match `<tag>{...`.
        val response = """<tool_call>{"name": "get_weather", "arguments": {"location": "NY"}}</tool_call>"""

        val result = parser.parse(response)

        assertThat(result.toolCalls).hasSize(1)
        assertThat(result.toolCalls[0].name).isEqualTo("get_weather")
        assertThat(result.toolCalls[0].arguments).contains("NY")
    }

    @Test
    fun `parse preserves existing JSON tool_call wrapper unchanged`() {
        // Regression guard: the canonical {"tool_call":{"name":...}} path
        // must still work after the hybrid addition.
        val response = """{"tool_call": {"name": "list_timers", "arguments": {}}}"""

        val result = parser.parse(response)

        assertThat(result.toolCalls).hasSize(1)
        assertThat(result.toolCalls[0].name).isEqualTo("list_timers")
    }

    @Test
    fun `parse preserves TOOL_CALL natural form unchanged`() {
        // Regression guard: the natural TOOL_CALL: form still works.
        val response = """TOOL_CALL: get_weather(location="Tokyo")"""

        val result = parser.parse(response)

        assertThat(result.toolCalls).hasSize(1)
        assertThat(result.toolCalls[0].name).isEqualTo("get_weather")
        assertThat(result.toolCalls[0].arguments).contains("Tokyo")
    }

    @Test
    fun `parse preserves fenced JSON block unchanged`() {
        // Regression guard: markdown-fenced JSON still parses.
        val response = """```json
{"tool_call": {"name": "get_datetime", "arguments": {}}}
```"""

        val result = parser.parse(response)

        assertThat(result.toolCalls).hasSize(1)
        assertThat(result.toolCalls[0].name).isEqualTo("get_datetime")
    }

    @Test
    fun `detectsRefusal returns true for common refusal phrases`() {
        assertThat(ToolCallParser.looksLikeRefusal("I don't have access to tools.")).isTrue()
        assertThat(ToolCallParser.looksLikeRefusal("I can't do that.")).isTrue()
        assertThat(ToolCallParser.looksLikeRefusal("私はツールを持っていません。")).isTrue()
        assertThat(ToolCallParser.looksLikeRefusal("できません")).isTrue()
        assertThat(ToolCallParser.looksLikeRefusal("持ってません")).isTrue()
    }

    @Test
    fun `detectsRefusal returns false for normal responses`() {
        assertThat(ToolCallParser.looksLikeRefusal("The weather is sunny.")).isFalse()
        assertThat(ToolCallParser.looksLikeRefusal("今日の天気は晴れです。")).isFalse()
        assertThat(ToolCallParser.looksLikeRefusal("")).isFalse()
    }
}
