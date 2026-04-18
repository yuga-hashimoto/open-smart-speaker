package com.opendash.app.tool.info

import com.google.common.truth.Truth.assertThat
import com.opendash.app.tool.ToolCall
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SearchToolExecutorTest {

    private lateinit var executor: SearchToolExecutor
    private lateinit var searchProvider: SearchProvider

    @BeforeEach
    fun setup() {
        searchProvider = mockk(relaxed = true)
        executor = SearchToolExecutor(searchProvider)
    }

    @Test
    fun `availableTools returns web_search`() = runTest {
        val tools = executor.availableTools()
        assertThat(tools.map { it.name }).contains("web_search")
    }

    @Test
    fun `web_search returns abstract and related topics`() = runTest {
        coEvery { searchProvider.search("Kotlin") } returns SearchResult(
            query = "Kotlin",
            abstract = "Kotlin is a programming language.",
            sourceUrl = "https://kotlinlang.org",
            relatedTopics = listOf("Android development", "JVM languages")
        )

        val result = executor.execute(
            ToolCall(id = "1", name = "web_search", arguments = mapOf("query" to "Kotlin"))
        )

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("Kotlin")
        assertThat(result.data).contains("programming language")
        assertThat(result.data).contains("Android development")
    }

    @Test
    fun `web_search without query returns error`() = runTest {
        val result = executor.execute(
            ToolCall(id = "2", name = "web_search", arguments = emptyMap())
        )

        assertThat(result.success).isFalse()
    }

    @Test
    fun `search escapes special characters in JSON`() = runTest {
        coEvery { searchProvider.search(any()) } returns SearchResult(
            query = "test",
            abstract = "Contains \"quotes\" and \\backslashes\\",
            sourceUrl = null,
            relatedTopics = emptyList()
        )

        val result = executor.execute(
            ToolCall(id = "3", name = "web_search", arguments = mapOf("query" to "test"))
        )

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("\\\"quotes\\\"")
    }
}
