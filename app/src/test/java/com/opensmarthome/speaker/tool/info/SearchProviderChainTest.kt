package com.opensmarthome.speaker.tool.info

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class SearchProviderChainTest {

    private class FixedProvider(val result: SearchResult) : SearchProvider {
        var called = 0
        override suspend fun search(query: String): SearchResult {
            called += 1
            return result
        }
    }

    private class ThrowingProvider(val message: String) : SearchProvider {
        override suspend fun search(query: String): SearchResult {
            throw RuntimeException(message)
        }
    }

    @Test
    fun `returns first non-empty result and skips later providers`() = runTest {
        val first = FixedProvider(
            SearchResult("q", "real answer", "https://x.test", emptyList())
        )
        val second = FixedProvider(
            SearchResult("q", "should not be used", null, emptyList())
        )

        val chain = SearchProviderChain(listOf(first, second))
        val result = chain.search("q")

        assertThat(result.abstract).isEqualTo("real answer")
        assertThat(first.called).isEqualTo(1)
        assertThat(second.called).isEqualTo(0)
    }

    @Test
    fun `falls through empty primary to secondary with content`() = runTest {
        val first = FixedProvider(
            SearchResult("q", "", null, emptyList())
        )
        val second = FixedProvider(
            SearchResult("q", "fallback answer", null, emptyList())
        )

        val chain = SearchProviderChain(listOf(first, second))
        val result = chain.search("q")

        assertThat(result.abstract).isEqualTo("fallback answer")
        assertThat(first.called).isEqualTo(1)
        assertThat(second.called).isEqualTo(1)
    }

    @Test
    fun `returns first empty result when all providers are empty`() = runTest {
        val first = FixedProvider(SearchResult("q", "", null, emptyList()))
        val second = FixedProvider(SearchResult("q", "", null, emptyList()))

        val chain = SearchProviderChain(listOf(first, second))
        val result = chain.search("q")

        assertThat(result.abstract).isEmpty()
    }

    @Test
    fun `uses secondary when primary throws`() = runTest {
        val first = ThrowingProvider("boom")
        val second = FixedProvider(
            SearchResult("q", "fallback", null, emptyList())
        )

        val chain = SearchProviderChain(listOf(first, second))
        val result = chain.search("q")

        assertThat(result.abstract).isEqualTo("fallback")
    }

    @Test
    fun `rethrows first exception when all providers throw`() = runTest {
        val chain = SearchProviderChain(
            listOf(ThrowingProvider("first"), ThrowingProvider("second"))
        )

        val err = assertThrows(RuntimeException::class.java) {
            kotlinx.coroutines.runBlocking { chain.search("q") }
        }
        assertThat(err.message).isEqualTo("first")
    }

    @Test
    fun `requires non-empty provider list`() {
        assertThrows(IllegalArgumentException::class.java) {
            SearchProviderChain(emptyList())
        }
    }
}
