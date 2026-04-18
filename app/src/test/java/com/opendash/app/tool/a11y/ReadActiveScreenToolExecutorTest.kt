package com.opendash.app.tool.a11y

import android.graphics.Rect
import com.google.common.truth.Truth.assertThat
import com.opendash.app.a11y.A11yServiceHolder
import com.opendash.app.a11y.NodeSummary
import com.opendash.app.a11y.OpenDashA11yService
import com.opendash.app.tool.ToolCall
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class ReadActiveScreenToolExecutorTest {

    private fun holder(
        service: OpenDashA11yService?,
        pkg: String? = null
    ): A11yServiceHolder {
        val h = mockk<A11yServiceHolder>(relaxed = true)
        every { h.serviceRef } returns service
        every { h.currentPackage } returns MutableStateFlow(pkg)
        return h
    }

    private fun rect(): Rect = mockk(relaxed = true)

    @Test
    fun `availableTools exposes read_active_screen`() = runTest {
        val executor = ReadActiveScreenToolExecutor(holder(null))

        val tools = executor.availableTools()

        assertThat(tools.map { it.name }).containsExactly("read_active_screen")
    }

    @Test
    fun `service not bound returns user-facing error`() = runTest {
        val executor = ReadActiveScreenToolExecutor(holder(service = null))

        val result = executor.execute(ToolCall("1", "read_active_screen", emptyMap()))

        assertThat(result.success).isFalse()
        assertThat(result.error).contains("Settings")
        assertThat(result.error).contains("Accessibility")
    }

    @Test
    fun `unknown tool name returns failure`() = runTest {
        val executor = ReadActiveScreenToolExecutor(holder(null))

        val result = executor.execute(ToolCall("1", "something_else", emptyMap()))

        assertThat(result.success).isFalse()
        assertThat(result.error).contains("Unknown tool")
    }

    @Test
    fun `service bound with nodes returns formatted markdown`() = runTest {
        val service = mockk<OpenDashA11yService>(relaxed = true)
        every { service.dumpActiveWindow() } returns listOf(
            NodeSummary(
                text = "Save",
                role = "button",
                contentDescription = null,
                clickable = true,
                bounds = rect()
            ),
            NodeSummary(
                text = "Your email:",
                role = "text",
                contentDescription = null,
                clickable = false,
                bounds = rect()
            ),
            NodeSummary(
                text = "current@email.com",
                role = "edit-text",
                contentDescription = null,
                clickable = false,
                bounds = rect()
            )
        )
        val executor = ReadActiveScreenToolExecutor(
            holder(service = service, pkg = "com.example.app")
        )

        val result = executor.execute(ToolCall("1", "read_active_screen", emptyMap()))

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("# Screen: com.example.app")
        assertThat(result.data).contains("""- [button] "Save" (clickable)""")
        assertThat(result.data).contains("""- [text] "Your email:"""")
        assertThat(result.data).contains("""- [edit-text] "current@email.com" (editable)""")
    }

    @Test
    fun `empty node list renders placeholder`() = runTest {
        val service = mockk<OpenDashA11yService>(relaxed = true)
        every { service.dumpActiveWindow() } returns emptyList()
        val executor = ReadActiveScreenToolExecutor(
            holder(service = service, pkg = "com.example.blank")
        )

        val result = executor.execute(ToolCall("1", "read_active_screen", emptyMap()))

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("# Screen: com.example.blank")
        assertThat(result.data).contains("(no visible text on screen)")
    }

    @Test
    fun `unknown package falls back in header`() = runTest {
        val service = mockk<OpenDashA11yService>(relaxed = true)
        every { service.dumpActiveWindow() } returns emptyList()
        every { service.currentForegroundPackage() } returns null
        val executor = ReadActiveScreenToolExecutor(
            holder(service = service, pkg = null)
        )

        val result = executor.execute(ToolCall("1", "read_active_screen", emptyMap()))

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("# Screen: (unknown)")
    }

    @Test
    fun `formatDump escapes embedded quotes`() {
        val nodes = listOf(
            NodeSummary(
                text = """Has "quote"""",
                role = "text",
                contentDescription = null,
                clickable = false,
                bounds = rect()
            )
        )

        val out = ReadActiveScreenToolExecutor.formatDump("pkg", nodes)

        assertThat(out).contains("""\"quote\"""")
    }

    @Test
    fun `formatDump uses contentDescription when text is null`() {
        val nodes = listOf(
            NodeSummary(
                text = null,
                role = "image",
                contentDescription = "avatar",
                clickable = true,
                bounds = rect()
            )
        )

        val out = ReadActiveScreenToolExecutor.formatDump("pkg", nodes)

        assertThat(out).contains("""- [image] "avatar" (clickable)""")
    }
}
