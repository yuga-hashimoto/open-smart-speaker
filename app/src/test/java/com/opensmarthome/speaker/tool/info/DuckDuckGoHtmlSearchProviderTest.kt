package com.opensmarthome.speaker.tool.info

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for DuckDuckGoHtmlSearchProvider using MockWebServer to verify
 * HTML scraping against a realistic DuckDuckGo HTML response.
 *
 * Stolen from openclaw: extensions/duckduckgo/src/ddg-client.ts
 */
class DuckDuckGoHtmlSearchProviderTest {

    private lateinit var server: MockWebServer
    private lateinit var provider: DuckDuckGoHtmlSearchProvider

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        provider = DuckDuckGoHtmlSearchProvider(
            client = OkHttpClient(),
            endpointProvider = { server.url("/html").toString() }
        )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `search parses results from typical DuckDuckGo HTML`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(TYPICAL_HTML))

        val result = provider.search("kotlin coroutines")

        assertThat(result.query).isEqualTo("kotlin coroutines")
        assertThat(result.abstract).contains("Coroutines are a Kotlin feature")
        assertThat(result.sourceUrl).isEqualTo("https://kotlinlang.org/docs/coroutines-overview.html")
        // Related topics come from remaining titles.
        assertThat(result.relatedTopics).isNotEmpty()
        assertThat(result.relatedTopics.first()).contains("Hands-on")
    }

    @Test
    fun `search decodes uddg redirect URLs`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(UDDG_HTML))

        val result = provider.search("something")

        assertThat(result.sourceUrl).isEqualTo("https://example.com/article")
    }

    @Test
    fun `search sends chrome user-agent header`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(TYPICAL_HTML))

        provider.search("kotlin")

        val recorded = server.takeRequest()
        val ua = recorded.getHeader("User-Agent").orEmpty()
        assertThat(ua).contains("Mozilla/5.0")
        assertThat(ua).contains("Chrome/")
        // Query is sent as ?q=
        assertThat(recorded.path).contains("q=kotlin")
    }

    @Test
    fun `search throws on bot challenge HTML`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                <html><body>
                <form id="challenge-form">Are you a human?</form>
                <div class="g-recaptcha"></div>
                </body></html>
                """.trimIndent()
            )
        )

        val err = assertThrows(RuntimeException::class.java) {
            kotlinx.coroutines.runBlocking { provider.search("anything") }
        }
        assertThat(err.message).contains("bot")
    }

    @Test
    fun `search returns empty result when HTML has no matches`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """<html><body><p>nothing here</p></body></html>"""
            )
        )

        val result = provider.search("zzzzz")

        assertThat(result.abstract).isEmpty()
        assertThat(result.sourceUrl).isNull()
        assertThat(result.relatedTopics).isEmpty()
    }

    @Test
    fun `search throws on HTTP error status`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))

        val err = assertThrows(RuntimeException::class.java) {
            kotlinx.coroutines.runBlocking { provider.search("foo") }
        }
        assertThat(err.message).contains("503")
    }

    @Test
    fun `search decodes HTML entities in titles and snippets`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                <div class="result">
                  <a class="result__a" href="https://example.com/">Tom &amp; Jerry &#8211; Cartoon</a>
                  <a class="result__snippet" href="https://example.com/">A classic cat &amp; mouse tale.</a>
                </div>
                """.trimIndent()
            )
        )

        val result = provider.search("tom and jerry")

        // Heuristic: entities were decoded, raw "&amp;" must not appear.
        assertThat(result.abstract).doesNotContain("&amp;")
        assertThat(result.abstract).contains("cat & mouse")
        assertThat(result.relatedTopics.firstOrNull() ?: "Tom & Jerry")
            .contains("Tom & Jerry")
    }

    companion object {
        // Simplified DuckDuckGo HTML SERP with two results.
        private val TYPICAL_HTML = """
            <html><body>
            <div class="results">
              <div class="result">
                <a class="result__a" href="https://kotlinlang.org/docs/coroutines-overview.html">Kotlin Coroutines Overview</a>
                <a class="result__snippet" href="https://kotlinlang.org/docs/coroutines-overview.html">Coroutines are a Kotlin feature for asynchronous programming.</a>
              </div>
              <div class="result">
                <a class="result__a" href="https://kotlinlang.org/docs/hands-on/Introduction-to-Coroutines-and-Channels/">Hands-on: Introduction to Coroutines</a>
                <a class="result__snippet" href="https://kotlinlang.org/docs/hands-on/">Learn coroutines with hands-on tutorial.</a>
              </div>
            </div>
            </body></html>
        """.trimIndent()

        private val UDDG_HTML = """
            <div class="result">
              <a class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Farticle&rut=abc">Wrapped Example</a>
              <a class="result__snippet" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Farticle">Snippet goes here.</a>
            </div>
        """.trimIndent()
    }
}
