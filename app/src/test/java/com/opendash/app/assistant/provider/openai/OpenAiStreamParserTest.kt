package com.opendash.app.assistant.provider.openai

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class OpenAiStreamParserTest {

    private lateinit var parser: OpenAiStreamParser
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    @BeforeEach
    fun setup() {
        parser = OpenAiStreamParser(moshi)
    }

    @Test
    fun `parseLine returns null for empty line`() {
        assertNull(parser.parseLine(""))
    }

    @Test
    fun `parseLine returns null for DONE marker`() {
        assertNull(parser.parseLine("data: [DONE]"))
    }

    @Test
    fun `parseLine returns null for non-data line`() {
        assertNull(parser.parseLine("event: message"))
    }

    @Test
    fun `parseLine extracts content delta`() {
        val json = """data: {"choices":[{"delta":{"content":"Hello"},"finish_reason":null}]}"""
        val result = parser.parseLine(json)
        assertNotNull(result)
        assertEquals("Hello", result!!.contentDelta)
        assertNull(result.finishReason)
    }

    @Test
    fun `parseLine extracts finish reason`() {
        val json = """data: {"choices":[{"delta":{},"finish_reason":"stop"}]}"""
        val result = parser.parseLine(json)
        assertNotNull(result)
        assertEquals("stop", result!!.finishReason)
    }

    @Test
    fun `parseLine handles tool call delta`() {
        val json = """data: {"choices":[{"delta":{"tool_calls":[{"id":"call_1","function":{"name":"get_entity_state","arguments":"{\"entity_id\":\"light.living\"}"}}]},"finish_reason":null}]}"""
        val result = parser.parseLine(json)
        assertNotNull(result)
        assertNotNull(result!!.toolCallDelta)
        assertEquals("get_entity_state", result.toolCallDelta!!.name)
        assertEquals("call_1", result.toolCallDelta!!.id)
    }

    @Test
    fun `parseLine returns null for malformed JSON`() {
        val result = parser.parseLine("data: {invalid json}")
        assertNull(result)
    }

    @Test
    fun `parseFullResponse extracts assistant message`() {
        val json = """{"choices":[{"message":{"role":"assistant","content":"Hello world"},"finish_reason":"stop"}]}"""
        val result = parser.parseFullResponse(json)
        val assistant = result as? com.opendash.app.assistant.model.AssistantMessage.Assistant
        assertNotNull(assistant)
        assertEquals("Hello world", assistant!!.content)
    }

    @Test
    fun `parseFullResponse extracts tool calls`() {
        val json = """{"choices":[{"message":{"role":"assistant","content":"","tool_calls":[{"id":"call_1","function":{"name":"turn_on","arguments":"{}"}}]},"finish_reason":"tool_calls"}]}"""
        val result = parser.parseFullResponse(json)
        val assistant = result as? com.opendash.app.assistant.model.AssistantMessage.Assistant
        assertNotNull(assistant)
        assertEquals(1, assistant!!.toolCalls.size)
        assertEquals("turn_on", assistant.toolCalls[0].name)
    }

    @Test
    fun `parseFullResponse handles empty response`() {
        val result = parser.parseFullResponse("{}")
        val assistant = result as? com.opendash.app.assistant.model.AssistantMessage.Assistant
        assertNotNull(assistant)
        assertEquals("", assistant!!.content)
    }

    @Test
    fun `parseLine handles whitespace in data prefix`() {
        val json = """  data: {"choices":[{"delta":{"content":"Hi"},"finish_reason":null}]}"""
        val result = parser.parseLine(json)
        assertNotNull(result)
        assertEquals("Hi", result!!.contentDelta)
    }
}
