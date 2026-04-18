package com.opendash.app.tool.a11y

import android.view.accessibility.AccessibilityNodeInfo
import com.google.common.truth.Truth.assertThat
import com.opendash.app.a11y.A11yServiceHolder
import com.opendash.app.a11y.OpenDashA11yService
import com.opendash.app.tool.ToolCall
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class TapByTextToolExecutorTest {

    private fun holder(service: OpenDashA11yService?): A11yServiceHolder {
        val h = mockk<A11yServiceHolder>(relaxed = true)
        every { h.serviceRef } returns service
        return h
    }

    @Test
    fun `availableTools exposes tap_by_text with text parameter`() = runTest {
        val executor = TapByTextToolExecutor(holder(null))

        val tools = executor.availableTools()

        assertThat(tools).hasSize(1)
        assertThat(tools.first().name).isEqualTo("tap_by_text")
        assertThat(tools.first().parameters).containsKey("text")
        assertThat(tools.first().parameters["text"]!!.required).isTrue()
    }

    @Test
    fun `service not bound returns user-facing error`() = runTest {
        val executor = TapByTextToolExecutor(holder(service = null))

        val result = executor.execute(
            ToolCall("1", "tap_by_text", mapOf("text" to "Save"))
        )

        assertThat(result.success).isFalse()
        assertThat(result.error).contains("Settings")
        assertThat(result.error).contains("Accessibility")
    }

    @Test
    fun `unknown tool name returns failure`() = runTest {
        val executor = TapByTextToolExecutor(holder(null))

        val result = executor.execute(ToolCall("1", "something_else", emptyMap()))

        assertThat(result.success).isFalse()
        assertThat(result.error).contains("Unknown tool")
    }

    @Test
    fun `empty text argument returns failure`() = runTest {
        val service = mockk<OpenDashA11yService>(relaxed = true)
        val executor = TapByTextToolExecutor(holder(service))

        val result = executor.execute(
            ToolCall("1", "tap_by_text", mapOf("text" to "   "))
        )

        assertThat(result.success).isFalse()
        assertThat(result.error).contains("non-empty")
    }

    @Test
    fun `missing text argument returns failure`() = runTest {
        val service = mockk<OpenDashA11yService>(relaxed = true)
        val executor = TapByTextToolExecutor(holder(service))

        val result = executor.execute(ToolCall("1", "tap_by_text", emptyMap()))

        assertThat(result.success).isFalse()
        assertThat(result.error).contains("non-empty")
    }

    @Test
    fun `no matching node returns failure`() = runTest {
        val service = mockk<OpenDashA11yService>(relaxed = true)
        every { service.findNodeByText("Save") } returns null
        val executor = TapByTextToolExecutor(holder(service))

        val result = executor.execute(
            ToolCall("1", "tap_by_text", mapOf("text" to "Save"))
        )

        assertThat(result.success).isFalse()
        assertThat(result.error).contains("No clickable element")
        assertThat(result.error).contains("Save")
    }

    @Test
    fun `happy path delegates to performTapOnNode`() = runTest {
        val service = mockk<OpenDashA11yService>(relaxed = true)
        val node = mockk<AccessibilityNodeInfo>(relaxed = true)
        every { service.findNodeByText("Save") } returns node
        every { service.performTapOnNode(node) } returns true
        val executor = TapByTextToolExecutor(holder(service))

        val result = executor.execute(
            ToolCall("1", "tap_by_text", mapOf("text" to "Save"))
        )

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("Save")
        verify { service.performTapOnNode(node) }
    }

    @Test
    fun `dispatch rejected returns failure without crashing`() = runTest {
        val service = mockk<OpenDashA11yService>(relaxed = true)
        val node = mockk<AccessibilityNodeInfo>(relaxed = true)
        every { service.findNodeByText("Save") } returns node
        every { service.performTapOnNode(node) } returns false
        val executor = TapByTextToolExecutor(holder(service))

        val result = executor.execute(
            ToolCall("1", "tap_by_text", mapOf("text" to "Save"))
        )

        assertThat(result.success).isFalse()
        assertThat(result.error).contains("rejected")
    }
}
