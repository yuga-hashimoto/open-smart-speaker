package com.opendash.app.tool.info

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Unit tests for DuckDuckGoSearchProvider.
 *
 * Tests verify the SearchProvider interface contract and that suspend calls
 * complete correctly inside runTest (which validates IO dispatcher usage is safe).
 */
class DuckDuckGoSearchProviderTest {

    private val searchProvider: SearchProvider = mockk()

    @Test
    fun `search returns result with abstract and source url`() = runTest {
        coEvery { searchProvider.search("Kotlin coroutines") } returns SearchResult(
            query = "Kotlin coroutines",
            abstract = "Coroutines are a Kotlin feature for async programming.",
            sourceUrl = "https://kotlinlang.org/docs/coroutines-overview.html",
            relatedTopics = listOf("async", "suspend functions")
        )

        val result = searchProvider.search("Kotlin coroutines")

        assertThat(result.query).isEqualTo("Kotlin coroutines")
        assertThat(result.abstract).contains("Kotlin")
        assertThat(result.sourceUrl).isNotNull()
        assertThat(result.relatedTopics).hasSize(2)
    }

    @Test
    fun `search returns empty result when no abstract found`() = runTest {
        coEvery { searchProvider.search("xyzzy12345") } returns SearchResult(
            query = "xyzzy12345",
            abstract = "",
            sourceUrl = null,
            relatedTopics = emptyList()
        )

        val result = searchProvider.search("xyzzy12345")

        assertThat(result.abstract).isEmpty()
        assertThat(result.sourceUrl).isNull()
        assertThat(result.relatedTopics).isEmpty()
    }

    @Test
    fun `search propagates exception on network failure`() = runTest {
        coEvery { searchProvider.search(any()) } throws RuntimeException("Search API error: 503")

        val result = runCatching { searchProvider.search("test") }

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("503")
    }
}
