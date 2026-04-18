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

class ScrollScreenToolExecutorTest {

    private fun holder(service: OpenDashA11yService?): A11yServiceHolder {
        val h = mockk<A11yServiceHolder>(relaxed = true)
        every { h.serviceRef } returns service
        return h
    }

    @Test
    fun `availableTools exposes scroll_screen with direction enum`() = runTest {
        val executor = ScrollScreenToolExecutor(holder(null))

        val tools = executor.availableTools()

        assertThat(tools).hasSize(1)
        val schema = tools.first()
        assertThat(schema.name).isEqualTo("scroll_screen")
        val direction = schema.parameters["direction"]
        assertThat(direction).isNotNull()
        assertThat(direction!!.required).isTrue()
        assertThat(direction.enum).containsExactly("up", "down", "left", "right").inOrder()
    }

    @Test
    fun `service not bound returns user-facing error`() = runTest {
        val executor = ScrollScreenToolExecutor(holder(service = null))

        val result = executor.execute(
            ToolCall("1", "scroll_screen", mapOf("direction" to "down"))
        )

        assertThat(result.success).isFalse()
        assertThat(result.error).contains("Settings")
        assertThat(result.error).contains("Accessibility")
    }

    @Test
    fun `unknown tool name returns failure`() = runTest {
        val executor = ScrollScreenToolExecutor(holder(null))

        val result = executor.execute(ToolCall("1", "nope", emptyMap()))

        assertThat(result.success).isFalse()
        assertThat(result.error).contains("Unknown tool")
    }

    @Test
    fun `unsupported direction returns failure`() = runTest {
        val service = mockk<OpenDashA11yService>(relaxed = true)
        val executor = ScrollScreenToolExecutor(holder(service))

        val result = executor.execute(
            ToolCall("1", "scroll_screen", mapOf("direction" to "diagonal"))
        )

        assertThat(result.success).isFalse()
        assertThat(result.error).contains("up")
        assertThat(result.error).contains("down")
    }

    @Test
    fun `happy path delegates to performSwipe`() = runTest {
        val service = mockk<OpenDashA11yService>(relaxed = true)
        every { service.performSwipe("down") } returns true
        val executor = ScrollScreenToolExecutor(holder(service))

        val result = executor.execute(
            ToolCall("1", "scroll_screen", mapOf("direction" to "DOWN"))
        )

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("down")
        verify { service.performSwipe("down") }
    }

    @Test
    fun `dispatch rejected returns failure`() = runTest {
        val service = mockk<OpenDashA11yService>(relaxed = true)
        every { service.performSwipe("up") } returns false
        val executor = ScrollScreenToolExecutor(holder(service))

        val result = executor.execute(
            ToolCall("1", "scroll_screen", mapOf("direction" to "up"))
        )

        assertThat(result.success).isFalse()
        assertThat(result.error).contains("rejected")
    }
}
