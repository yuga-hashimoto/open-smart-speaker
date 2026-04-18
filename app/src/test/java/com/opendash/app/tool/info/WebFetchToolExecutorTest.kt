package com.opendash.app.tool.info

import com.google.common.truth.Truth.assertThat
import com.opendash.app.tool.ToolCall
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class WebFetchToolExecutorTest {

    private lateinit var fetcher: WebFetcher
    private lateinit var executor: WebFetchToolExecutor

    @BeforeEach
    fun setup() {
        fetcher = mockk()
        executor = WebFetchToolExecutor(fetcher)
    }

    @Test
    fun `availableTools has web_fetch`() = runTest {
        assertThat(executor.availableTools().map { it.name }).containsExactly("web_fetch")
    }

    @Test
    fun `fetch success returns page data`() = runTest {
        coEvery { fetcher.fetch("https://example.com", 4000) } returns WebPage(
            url = "https://example.com",
            title = "Example Domain",
            text = "This is example text",
            statusCode = 200,
            contentType = "text/html"
        )

        val result = executor.execute(
            ToolCall("1", "web_fetch", mapOf("url" to "https://example.com"))
        )

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("Example Domain")
        assertThat(result.data).contains("example text")
    }

    @Test
    fun `missing url returns error`() = runTest {
        val result = executor.execute(ToolCall("2", "web_fetch", emptyMap()))
        assertThat(result.success).isFalse()
    }

    @Test
    fun `non-http url returns error`() = runTest {
        val result = executor.execute(
            ToolCall("3", "web_fetch", mapOf("url" to "file:///etc/passwd"))
        )
        assertThat(result.success).isFalse()
        assertThat(result.error).contains("http")
    }

    @Test
    fun `non-2xx status is reported as failure`() = runTest {
        coEvery { fetcher.fetch(any(), any()) } returns WebPage(
            "https://example.com", "err", "", 404, "text/html"
        )

        val result = executor.execute(
            ToolCall("4", "web_fetch", mapOf("url" to "https://example.com"))
        )

        assertThat(result.success).isFalse()
        assertThat(result.error).contains("404")
    }

    @Test
    fun `max_chars is passed through clamped`() = runTest {
        var capturedMax = -1
        coEvery { fetcher.fetch(any(), any()) } coAnswers {
            capturedMax = secondArg()
            WebPage("https://x", "", "", 200, "")
        }

        executor.execute(
            ToolCall("5", "web_fetch", mapOf("url" to "https://x", "max_chars" to 1000000.0))
        )

        assertThat(capturedMax).isEqualTo(16000)
    }
}
