package com.opensmarthome.speaker.tool.system

import com.google.common.truth.Truth.assertThat
import com.opensmarthome.speaker.tool.ToolCall
import com.opensmarthome.speaker.util.LocaleManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class LocaleToolExecutorTest {

    private val bundled = listOf(
        LocaleManager.Option("", "System default"),
        LocaleManager.Option("en", "English"),
        LocaleManager.Option("ja", "日本語")
    )

    private fun manager(currentTag: String = "", overrideSupported: Boolean = true): LocaleManager {
        val m = mockk<LocaleManager>(relaxed = true)
        io.mockk.every { m.options } returns bundled
        io.mockk.every { m.isOverrideSupported } returns overrideSupported
        coEvery { m.currentTag() } returns currentTag
        return m
    }

    @Test
    fun `get_app_locale returns the persisted tag and label`() = runTest {
        val exec = LocaleToolExecutor(manager(currentTag = "ja"))
        val result = exec.execute(ToolCall(id = "1", name = "get_app_locale", arguments = emptyMap()))

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("\"tag\":\"ja\"")
        assertThat(result.data).contains("\"label\":\"日本語\"")
        assertThat(result.data).contains("\"override_supported\":true")
    }

    @Test
    fun `get_app_locale flags unknown tag as Unknown label`() = runTest {
        val exec = LocaleToolExecutor(manager(currentTag = "xx"))
        val result = exec.execute(ToolCall(id = "2", name = "get_app_locale", arguments = emptyMap()))

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("\"label\":\"Unknown\"")
    }

    @Test
    fun `list_app_locales returns every bundled option`() = runTest {
        val exec = LocaleToolExecutor(manager())
        val result = exec.execute(ToolCall(id = "3", name = "list_app_locales", arguments = emptyMap()))

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("\"tag\":\"en\"")
        assertThat(result.data).contains("\"tag\":\"ja\"")
        assertThat(result.data).contains("\"label\":\"日本語\"")
    }

    @Test
    fun `set_app_locale accepts a bundled tag and invokes LocaleManager apply`() = runTest {
        val m = manager()
        val exec = LocaleToolExecutor(m)
        val result = exec.execute(
            ToolCall(id = "4", name = "set_app_locale", arguments = mapOf("tag" to "ja"))
        )

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("\"tag\":\"ja\"")
        coVerify { m.apply("ja") }
    }

    @Test
    fun `set_app_locale accepts empty string as follow-system`() = runTest {
        val m = manager()
        val exec = LocaleToolExecutor(m)
        val result = exec.execute(
            ToolCall(id = "5", name = "set_app_locale", arguments = mapOf("tag" to ""))
        )

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("\"label\":\"System default\"")
        coVerify { m.apply("") }
    }

    @Test
    fun `set_app_locale rejects unsupported tag and surfaces the available list`() = runTest {
        val m = manager()
        val exec = LocaleToolExecutor(m)
        val result = exec.execute(
            ToolCall(id = "6", name = "set_app_locale", arguments = mapOf("tag" to "xx"))
        )

        assertThat(result.success).isFalse()
        assertThat(result.error).contains("Unsupported locale 'xx'")
        assertThat(result.error).contains("\"ja\"")
        coVerify(exactly = 0) { m.apply(any()) }
    }

    @Test
    fun `set_app_locale rejects a missing tag argument`() = runTest {
        val exec = LocaleToolExecutor(manager())
        val result = exec.execute(
            ToolCall(id = "7", name = "set_app_locale", arguments = emptyMap())
        )

        assertThat(result.success).isFalse()
        assertThat(result.error).contains("Missing 'tag'")
    }

    @Test
    fun `unknown tool name returns failure`() = runTest {
        val exec = LocaleToolExecutor(manager())
        val result = exec.execute(ToolCall(id = "8", name = "bogus", arguments = emptyMap()))

        assertThat(result.success).isFalse()
        assertThat(result.error).contains("Unknown tool")
    }

    @Test
    fun `override_supported false is reflected in the response JSON`() = runTest {
        val exec = LocaleToolExecutor(manager(currentTag = "ja", overrideSupported = false))
        val result = exec.execute(ToolCall(id = "9", name = "get_app_locale", arguments = emptyMap()))

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("\"override_supported\":false")
    }
}
