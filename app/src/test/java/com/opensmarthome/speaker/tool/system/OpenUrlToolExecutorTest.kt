package com.opensmarthome.speaker.tool.system

import android.content.Context
import android.content.Intent
import com.google.common.truth.Truth.assertThat
import com.opensmarthome.speaker.tool.ToolCall
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class OpenUrlToolExecutorTest {

    private data class Harness(
        val executor: OpenUrlToolExecutor,
        val context: Context,
        val intent: Intent,
        val requestedUrl: () -> String?,
        val flagsSet: () -> List<Int>,
        val startedIntents: List<Intent>
    )

    private fun newHarness(): Harness {
        val ctx = mockk<Context>(relaxed = true)
        val intent = mockk<Intent>(relaxed = true)
        var requestedUrl: String? = null
        val flagsSet = mutableListOf<Int>()
        val startedIntents = mutableListOf<Intent>()

        every { intent.addFlags(any()) } answers {
            flagsSet.add(firstArg())
            intent
        }
        every { ctx.startActivity(any()) } answers {
            startedIntents.add(firstArg())
            Unit
        }

        val exec = OpenUrlToolExecutor(
            context = ctx,
            intentFactory = { url ->
                requestedUrl = url
                intent
            }
        )
        return Harness(exec, ctx, intent, { requestedUrl }, { flagsSet.toList() }, startedIntents)
    }

    @Test
    fun `availableTools exposes open_url with url param`() = runTest {
        val h = newHarness()
        val tools = h.executor.availableTools()
        assertThat(tools.map { it.name }).containsExactly("open_url")
        val param = tools.first().parameters["url"]
        assertThat(param).isNotNull()
        assertThat(param!!.required).isTrue()
        assertThat(param.type).isEqualTo("string")
    }

    @Test
    fun `valid https url dispatches VIEW intent with NEW_TASK flag`() = runTest {
        val h = newHarness()
        val result = h.executor.execute(
            ToolCall(id = "1", name = "open_url", arguments = mapOf("url" to "https://example.com/path"))
        )
        assertThat(result.success).isTrue()
        assertThat(result.data).contains("\"url\":\"https://example.com/path\"")
        assertThat(result.data).contains("\"host\":\"example.com\"")
        assertThat(h.requestedUrl()).isEqualTo("https://example.com/path")
        assertThat(h.flagsSet()).contains(Intent.FLAG_ACTIVITY_NEW_TASK)
        assertThat(h.startedIntents).hasSize(1)
    }

    @Test
    fun `valid http url also dispatched`() = runTest {
        val h = newHarness()
        val result = h.executor.execute(
            ToolCall(id = "2", name = "open_url", arguments = mapOf("url" to "http://news.example.org"))
        )
        assertThat(result.success).isTrue()
        assertThat(h.requestedUrl()).isEqualTo("http://news.example.org")
        assertThat(h.startedIntents).hasSize(1)
    }

    @Test
    fun `javascript scheme rejected`() = runTest {
        val h = newHarness()
        val result = h.executor.execute(
            ToolCall(id = "x", name = "open_url", arguments = mapOf("url" to "javascript:alert(1)"))
        )
        assertThat(result.success).isFalse()
        assertThat(result.error).contains("Only http/https")
        assertThat(h.startedIntents).isEmpty()
        verify(exactly = 0) { h.context.startActivity(any()) }
    }

    @Test
    fun `intent scheme rejected`() = runTest {
        val h = newHarness()
        val result = h.executor.execute(
            ToolCall(id = "x", name = "open_url", arguments = mapOf("url" to "intent://scan/#Intent;scheme=zxing;end"))
        )
        assertThat(result.success).isFalse()
        assertThat(result.error).contains("Only http/https")
        assertThat(h.startedIntents).isEmpty()
    }

    @Test
    fun `content scheme rejected`() = runTest {
        val h = newHarness()
        val result = h.executor.execute(
            ToolCall(id = "x", name = "open_url", arguments = mapOf("url" to "content://com.android.contacts/contacts/1"))
        )
        assertThat(result.success).isFalse()
        assertThat(result.error).contains("Only http/https")
        assertThat(h.startedIntents).isEmpty()
    }

    @Test
    fun `file scheme rejected`() = runTest {
        val h = newHarness()
        val result = h.executor.execute(
            ToolCall(id = "x", name = "open_url", arguments = mapOf("url" to "file:///etc/passwd"))
        )
        assertThat(result.success).isFalse()
        assertThat(result.error).contains("Only http/https")
        assertThat(h.startedIntents).isEmpty()
    }

    @Test
    fun `malformed URI returns failure`() = runTest {
        val h = newHarness()
        val result = h.executor.execute(
            ToolCall(id = "x", name = "open_url", arguments = mapOf("url" to "ht tp://bad uri with spaces"))
        )
        assertThat(result.success).isFalse()
        assertThat(h.startedIntents).isEmpty()
    }

    @Test
    fun `missing url argument returns failure`() = runTest {
        val h = newHarness()
        val result = h.executor.execute(
            ToolCall(id = "x", name = "open_url", arguments = emptyMap())
        )
        assertThat(result.success).isFalse()
        assertThat(result.error).contains("Missing url")
        assertThat(h.startedIntents).isEmpty()
    }

    @Test
    fun `unknown tool name returns failure`() = runTest {
        val h = newHarness()
        val result = h.executor.execute(
            ToolCall(id = "x", name = "some_other_tool", arguments = mapOf("url" to "https://example.com"))
        )
        assertThat(result.success).isFalse()
    }

    @Test
    fun `empty url argument returns failure`() = runTest {
        val h = newHarness()
        val result = h.executor.execute(
            ToolCall(id = "x", name = "open_url", arguments = mapOf("url" to ""))
        )
        assertThat(result.success).isFalse()
        assertThat(h.startedIntents).isEmpty()
    }

    @Test
    fun `url without host returns failure`() = runTest {
        val h = newHarness()
        val result = h.executor.execute(
            ToolCall(id = "x", name = "open_url", arguments = mapOf("url" to "https:///path"))
        )
        assertThat(result.success).isFalse()
        assertThat(h.startedIntents).isEmpty()
    }
}
