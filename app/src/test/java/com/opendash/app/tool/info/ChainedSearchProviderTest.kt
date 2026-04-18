package com.opendash.app.tool.info

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Unit tests for ChainedSearchProvider, which falls back to a secondary
 * SearchProvider (Wikipedia) when the primary (DuckDuckGo Instant Answer)
 * returns an empty abstract and no related topics.
 */
class ChainedSearchProviderTest {

    private class FakeSearchProvider(
        private val result: SearchResult,
        private val error: Throwable? = null
    ) : SearchProvider {
        var callCount: Int = 0
        var lastQuery: String? = null

        override suspend fun search(query: String): SearchResult {
            callCount++
            lastQuery = query
            error?.let { throw it }
            return result
        }
    }

    private class FakeLanguageSearchProvider(
        private val resultsByLang: Map<String, SearchResult?>
    ) : LanguageAwareSearchProvider {
        val callLog: MutableList<Pair<String, String>> = mutableListOf()

        override suspend fun search(query: String, lang: String): SearchResult? {
            callLog.add(query to lang)
            return resultsByLang[lang]
        }
    }

    @Test
    fun `primary abstract present returns primary without calling fallback`() = runTest {
        val primary = FakeSearchProvider(
            SearchResult(
                query = "Kotlin",
                abstract = "Kotlin is a JVM language.",
                sourceUrl = "https://duckduckgo.com/Kotlin",
                relatedTopics = emptyList()
            )
        )
        val fallback = FakeLanguageSearchProvider(emptyMap())

        val chained = ChainedSearchProvider(primary, fallback)
        val result = chained.search("Kotlin")

        assertThat(result.abstract).isEqualTo("Kotlin is a JVM language.")
        assertThat(primary.callCount).isEqualTo(1)
        assertThat(fallback.callLog).isEmpty()
    }

    @Test
    fun `primary related topics present returns primary without calling fallback`() = runTest {
        val primary = FakeSearchProvider(
            SearchResult(
                query = "Kotlin",
                abstract = "",
                sourceUrl = null,
                relatedTopics = listOf("coroutines", "null safety")
            )
        )
        val fallback = FakeLanguageSearchProvider(emptyMap())

        val chained = ChainedSearchProvider(primary, fallback)
        val result = chained.search("Kotlin")

        assertThat(result.relatedTopics).hasSize(2)
        assertThat(fallback.callLog).isEmpty()
    }

    @Test
    fun `primary empty falls back to Wikipedia ja when available`() = runTest {
        val primary = FakeSearchProvider(
            SearchResult(
                query = "コトリン",
                abstract = "",
                sourceUrl = null,
                relatedTopics = emptyList()
            )
        )
        val fallback = FakeLanguageSearchProvider(
            mapOf(
                "ja" to SearchResult(
                    query = "コトリン",
                    abstract = "Kotlin は JVM 言語です。",
                    sourceUrl = "https://ja.wikipedia.org/wiki/Kotlin",
                    relatedTopics = emptyList()
                )
            )
        )

        val chained = ChainedSearchProvider(primary, fallback)
        val result = chained.search("コトリン")

        assertThat(result.abstract).contains("Kotlin は JVM")
        assertThat(result.sourceUrl).isEqualTo("https://ja.wikipedia.org/wiki/Kotlin")
        assertThat(fallback.callLog).hasSize(1)
        assertThat(fallback.callLog[0].second).isEqualTo("ja")
    }

    @Test
    fun `primary empty and ja empty falls back to en`() = runTest {
        val primary = FakeSearchProvider(
            SearchResult(
                query = "Rust programming",
                abstract = "",
                sourceUrl = null,
                relatedTopics = emptyList()
            )
        )
        val fallback = FakeLanguageSearchProvider(
            mapOf(
                "ja" to null,
                "en" to SearchResult(
                    query = "Rust programming",
                    abstract = "Rust is a systems language.",
                    sourceUrl = "https://en.wikipedia.org/wiki/Rust_(programming_language)",
                    relatedTopics = emptyList()
                )
            )
        )

        val chained = ChainedSearchProvider(primary, fallback)
        val result = chained.search("Rust programming")

        assertThat(result.abstract).contains("Rust is a systems language")
        assertThat(fallback.callLog).containsExactly("Rust programming" to "ja", "Rust programming" to "en")
    }

    @Test
    fun `all empty returns primary empty result`() = runTest {
        val primaryResult = SearchResult(
            query = "xyzzy",
            abstract = "",
            sourceUrl = null,
            relatedTopics = emptyList()
        )
        val primary = FakeSearchProvider(primaryResult)
        val fallback = FakeLanguageSearchProvider(mapOf("ja" to null, "en" to null))

        val chained = ChainedSearchProvider(primary, fallback)
        val result = chained.search("xyzzy")

        assertThat(result).isEqualTo(primaryResult)
        assertThat(fallback.callLog).hasSize(2)
    }

    @Test
    fun `primary throws is caught and falls back to Wikipedia`() = runTest {
        val primary = FakeSearchProvider(
            result = SearchResult("unused", "", null, emptyList()),
            error = RuntimeException("Search API error: 503")
        )
        val fallback = FakeLanguageSearchProvider(
            mapOf(
                "ja" to SearchResult(
                    query = "Python",
                    abstract = "Python は汎用プログラミング言語です。",
                    sourceUrl = "https://ja.wikipedia.org/wiki/Python",
                    relatedTopics = emptyList()
                )
            )
        )

        val chained = ChainedSearchProvider(primary, fallback)
        val result = chained.search("Python")

        assertThat(result.abstract).contains("Python")
        assertThat(result.sourceUrl).contains("wikipedia")
    }

    @Test
    fun `primary throws and fallback empty rethrows primary error`() = runTest {
        val primary = FakeSearchProvider(
            result = SearchResult("unused", "", null, emptyList()),
            error = RuntimeException("Search API error: 503")
        )
        val fallback = FakeLanguageSearchProvider(mapOf("ja" to null, "en" to null))

        val chained = ChainedSearchProvider(primary, fallback)
        val thrown = runCatching { chained.search("unknown") }

        assertThat(thrown.isFailure).isTrue()
        assertThat(thrown.exceptionOrNull()?.message).contains("503")
    }
}
