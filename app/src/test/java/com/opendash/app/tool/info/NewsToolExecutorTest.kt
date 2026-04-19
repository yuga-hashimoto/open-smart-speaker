package com.opendash.app.tool.info

import com.google.common.truth.Truth.assertThat
import com.opendash.app.tool.ToolCall
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NewsToolExecutorTest {

    private lateinit var executor: NewsToolExecutor
    private lateinit var provider: NewsProvider

    @BeforeEach
    fun setup() {
        provider = mockk(relaxed = true)
        executor = NewsToolExecutor(provider)
    }

    @Test
    fun `availableTools includes get_news`() = runTest {
        assertThat(executor.availableTools().map { it.name }).containsExactly("get_news")
    }

    @Test
    fun `get_news with feed_url returns items`() = runTest {
        coEvery { provider.getHeadlines("https://example.com/rss", 5) } returns listOf(
            NewsItem("Title 1", "Summary 1", "https://link/1", "2026-04-16")
        )

        val result = executor.execute(
            ToolCall("1", "get_news", mapOf("feed_url" to "https://example.com/rss"))
        )

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("Title 1")
        assertThat(result.data).contains("https://link/1")
    }

    @Test
    fun `get_news with bundled source resolves url`() = runTest {
        coEvery { provider.getHeadlines(any(), any()) } returns emptyList()

        val result = executor.execute(
            ToolCall("2", "get_news", mapOf("source" to "bbc"))
        )

        assertThat(result.success).isTrue()
    }

    @Test
    fun `get_news without source or url falls back to NHK general`() = runTest {
        var capturedUrl: String? = null
        coEvery { provider.getHeadlines(any(), any()) } coAnswers {
            capturedUrl = firstArg()
            emptyList()
        }

        val result = executor.execute(
            ToolCall("3", "get_news", emptyMap())
        )

        assertThat(result.success).isTrue()
        assertThat(capturedUrl).isEqualTo(BundledNewsFeeds.NHK_GENERAL.url)
    }

    @Test
    fun `get_news with unknown source falls back to NHK general`() = runTest {
        var capturedUrl: String? = null
        coEvery { provider.getHeadlines(any(), any()) } coAnswers {
            capturedUrl = firstArg()
            emptyList()
        }

        val result = executor.execute(
            ToolCall("4", "get_news", mapOf("source" to "nonexistent"))
        )

        assertThat(result.success).isTrue()
        assertThat(capturedUrl).isEqualTo(BundledNewsFeeds.NHK_GENERAL.url)
    }

    @Test
    fun `get_news uses user-configured default feed when no args provided`() = runTest {
        val userFeed = "https://example.com/user-default.rss"
        val executorWithUserDefault = NewsToolExecutor(
            provider,
            defaultFeedUrlProvider = { userFeed }
        )
        var capturedUrl: String? = null
        coEvery { provider.getHeadlines(any(), any()) } coAnswers {
            capturedUrl = firstArg()
            emptyList()
        }

        val result = executorWithUserDefault.execute(
            ToolCall("6", "get_news", emptyMap())
        )

        assertThat(result.success).isTrue()
        assertThat(capturedUrl).isEqualTo(userFeed)
    }

    @Test
    fun `get_news user-configured default is ignored when explicit feed_url is given`() = runTest {
        val userFeed = "https://example.com/user-default.rss"
        val executorWithUserDefault = NewsToolExecutor(
            provider,
            defaultFeedUrlProvider = { userFeed }
        )
        var capturedUrl: String? = null
        coEvery { provider.getHeadlines(any(), any()) } coAnswers {
            capturedUrl = firstArg()
            emptyList()
        }

        executorWithUserDefault.execute(
            ToolCall("7", "get_news", mapOf("feed_url" to "https://explicit.example/rss"))
        )

        assertThat(capturedUrl).isEqualTo("https://explicit.example/rss")
    }

    @Test
    fun `get_news falls back to NHK when user-configured default is blank`() = runTest {
        val executorWithBlankDefault = NewsToolExecutor(
            provider,
            defaultFeedUrlProvider = { "   " }
        )
        var capturedUrl: String? = null
        coEvery { provider.getHeadlines(any(), any()) } coAnswers {
            capturedUrl = firstArg()
            emptyList()
        }

        val result = executorWithBlankDefault.execute(
            ToolCall("8", "get_news", emptyMap())
        )

        assertThat(result.success).isTrue()
        assertThat(capturedUrl).isEqualTo(BundledNewsFeeds.NHK_GENERAL.url)
    }

    @Test
    fun `get_news respects limit`() = runTest {
        var capturedLimit = -1
        coEvery { provider.getHeadlines(any(), any()) } coAnswers {
            capturedLimit = secondArg()
            emptyList()
        }

        executor.execute(
            ToolCall("5", "get_news", mapOf("source" to "bbc", "limit" to 3.0))
        )

        assertThat(capturedLimit).isEqualTo(3)
    }
}
