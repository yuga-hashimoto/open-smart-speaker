package com.opendash.app.tool.a11y

import com.google.common.truth.Truth.assertThat
import com.opendash.app.a11y.A11yServiceHolder
import com.opendash.app.a11y.OpenDashA11yService
import com.opendash.app.tool.ToolCall
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class TypeTextToolExecutorTest {

    private fun holder(service: OpenDashA11yService?): A11yServiceHolder {
        val h = mockk<A11yServiceHolder>(relaxed = true)
        every { h.serviceRef } returns service
        return h
    }

    @Test
    fun `availableTools exposes type_text with text parameter`() = runTest {
        val executor = TypeTextToolExecutor(holder(null))

        val tools = executor.availableTools()

        assertThat(tools).hasSize(1)
        assertThat(tools.first().name).isEqualTo("type_text")
        assertThat(tools.first().parameters).containsKey("text")
        assertThat(tools.first().parameters["text"]!!.required).isTrue()
    }

    @Test
    fun `service not bound returns user-facing error`() = runTest {
        val executor = TypeTextToolExecutor(holder(service = null))

        val result = executor.execute(
            ToolCall("1", "type_text", mapOf("text" to "hello"))
        )

        assertThat(result.success).isFalse()
        assertThat(result.error).contains("Settings")
        assertThat(result.error).contains("Accessibility")
    }

    @Test
    fun `unknown tool name returns failure`() = runTest {
        val executor = TypeTextToolExecutor(holder(null))

        val result = executor.execute(ToolCall("1", "nope", emptyMap()))

        assertThat(result.success).isFalse()
        assertThat(result.error).contains("Unknown tool")
    }

    @Test
    fun `empty text argument returns failure`() = runTest {
        val service = mockk<OpenDashA11yService>(relaxed = true)
        val executor = TypeTextToolExecutor(holder(service))

        val result = executor.execute(
            ToolCall("1", "type_text", mapOf("text" to ""))
        )

        assertThat(result.success).isFalse()
        assertThat(result.error).contains("non-empty")
    }

    @Test
    fun `missing text argument returns failure`() = runTest {
        val service = mockk<OpenDashA11yService>(relaxed = true)
        val executor = TypeTextToolExecutor(holder(service))

        val result = executor.execute(ToolCall("1", "type_text", emptyMap()))

        assertThat(result.success).isFalse()
        assertThat(result.error).contains("non-empty")
    }

    @Test
    fun `happy path delegates to typeIntoFocused`() = runTest {
        val service = mockk<OpenDashA11yService>(relaxed = true)
        every { service.typeIntoFocused("hello world") } returns true
        val executor = TypeTextToolExecutor(holder(service))

        val result = executor.execute(
            ToolCall("1", "type_text", mapOf("text" to "hello world"))
        )

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("11")
        verify { service.typeIntoFocused("hello world") }
    }

    @Test
    fun `service returns false emits guidance about focus or paste`() = runTest {
        val service = mockk<OpenDashA11yService>(relaxed = true)
        every { service.typeIntoFocused(any()) } returns false
        val executor = TypeTextToolExecutor(holder(service))

        val result = executor.execute(
            ToolCall("1", "type_text", mapOf("text" to "hi"))
        )

        assertThat(result.success).isFalse()
        assertThat(result.error).contains("focused")
    }
}
