package com.opendash.app.tool.accessibility

import com.google.common.truth.Truth.assertThat
import com.opendash.app.tool.ToolCall
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ScreenToolExecutorTest {

    private lateinit var reader: ScreenReader
    private lateinit var executor: ScreenToolExecutor

    @BeforeEach
    fun setup() {
        reader = mockk()
        executor = ScreenToolExecutor(reader)
    }

    @Test
    fun `availableTools exposes read_screen`() = runTest {
        assertThat(executor.availableTools().map { it.name }).containsExactly("read_screen")
    }

    @Test
    fun `not ready returns instruction-rich error`() = runTest {
        every { reader.isReady() } returns false

        val result = executor.execute(
            ToolCall("1", "read_screen", emptyMap())
        )

        assertThat(result.success).isFalse()
        assertThat(result.error).contains("Accessibility")
    }

    @Test
    fun `ready returns snapshot JSON`() = runTest {
        every { reader.isReady() } returns true
        every { reader.readScreen() } returns ScreenSnapshot(
            packageName = "com.example.app",
            visibleTexts = listOf("Hello", "World"),
            clickableLabels = listOf("Send")
        )

        val result = executor.execute(
            ToolCall("2", "read_screen", emptyMap())
        )

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("com.example.app")
        assertThat(result.data).contains("Hello")
        assertThat(result.data).contains("Send")
    }

    @Test
    fun `null snapshot returns error`() = runTest {
        every { reader.isReady() } returns true
        every { reader.readScreen() } returns null

        val result = executor.execute(
            ToolCall("3", "read_screen", emptyMap())
        )

        assertThat(result.success).isFalse()
    }

    @Test
    fun `special chars in text are escaped`() = runTest {
        every { reader.isReady() } returns true
        every { reader.readScreen() } returns ScreenSnapshot(
            packageName = "app",
            visibleTexts = listOf("""Has "quote""""),
            clickableLabels = emptyList()
        )

        val result = executor.execute(
            ToolCall("4", "read_screen", emptyMap())
        )

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("\\\"quote\\\"")
    }
}
