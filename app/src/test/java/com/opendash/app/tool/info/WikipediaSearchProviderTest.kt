package com.opendash.app.tool.info

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for WikipediaSearchProvider using MockWebServer to exercise the
 * open-search → summary two-step flow and the various empty/null paths.
 */
class WikipediaSearchProviderTest {

    private lateinit var server: MockWebServer
    private lateinit var provider: WikipediaSearchProvider
    private val moshi = Moshi.Builder().build()

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        provider = WikipediaSearchProvider(
            client = OkHttpClient(),
            moshi = moshi,
            baseUrlProvider = { lang ->
                // Route both ja and en mock servers through the same MockWebServer
                // and encode the language as a path prefix so requests are distinguishable.
                server.url("/$lang").toString()
            }
        )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `search returns summary extract when open-search and summary succeed`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """["Android",["Android (operating system)"],[""],["https://ja.wikipedia.org/wiki/Android_(operating_system)"]]"""
            )
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"title":"Android (operating system)","extract":"Android is a mobile OS developed by Google.","content_urls":{"desktop":{"page":"https://ja.wikipedia.org/wiki/Android_(operating_system)"}}}"""
            )
        )

        val result = provider.search("Android", "ja")

        assertThat(result).isNotNull()
        assertThat(result!!.query).isEqualTo("Android")
        assertThat(result.abstract).contains("Android is a mobile OS")
        assertThat(result.sourceUrl).isEqualTo("https://ja.wikipedia.org/wiki/Android_(operating_system)")
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test
    fun `search returns null when open-search returns no titles`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("""["xyzzy12345",[],[],[]]""")
        )

        val result = provider.search("xyzzy12345", "ja")

        assertThat(result).isNull()
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun `search returns null when summary extract is blank`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """["Empty",["Empty Page"],[""],["https://ja.wikipedia.org/wiki/Empty_Page"]]"""
            )
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"title":"Empty Page","extract":""}"""
            )
        )

        val result = provider.search("Empty", "ja")

        assertThat(result).isNull()
    }

    @Test
    fun `search returns null when summary API errors`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """["Android",["Android"],[""],["https://ja.wikipedia.org/wiki/Android"]]"""
            )
        )
        server.enqueue(MockResponse().setResponseCode(404))

        val result = provider.search("Android", "ja")

        assertThat(result).isNull()
    }

    @Test
    fun `search returns null when open-search API errors`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))

        val result = provider.search("Android", "ja")

        assertThat(result).isNull()
    }

    @Test
    fun `search uses language-specific URL`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """["Android",["Android"],[""],["https://en.wikipedia.org/wiki/Android"]]"""
            )
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"title":"Android","extract":"Android is an OS."}"""
            )
        )

        val result = provider.search("Android", "en")

        assertThat(result).isNotNull()
        val first = server.takeRequest()
        assertThat(first.path).contains("/en/")
    }

    @Test
    fun `open-search request includes Wikimedia-policy User-Agent header`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """["Android",["Android"],[""],["https://ja.wikipedia.org/wiki/Android"]]"""
            )
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"title":"Android","extract":"Android is an OS."}"""
            )
        )

        provider.search("Android", "ja")

        val openSearchRequest = server.takeRequest()
        val userAgent = openSearchRequest.getHeader("User-Agent")
        assertThat(userAgent).isNotNull()
        // Wikimedia User-Agent policy: identify app + include a contact URL/email.
        // https://meta.wikimedia.org/wiki/User-Agent_policy
        assertThat(userAgent).contains("OpenDash")
        assertThat(userAgent).contains("https://github.com/")
    }

    @Test
    fun `summary request includes Wikimedia-policy User-Agent header`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """["Android",["Android"],[""],["https://ja.wikipedia.org/wiki/Android"]]"""
            )
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"title":"Android","extract":"Android is an OS."}"""
            )
        )

        provider.search("Android", "ja")

        // Skip the open-search request; inspect the summary request that follows.
        server.takeRequest()
        val summaryRequest = server.takeRequest()
        val userAgent = summaryRequest.getHeader("User-Agent")
        assertThat(userAgent).isNotNull()
        assertThat(userAgent).contains("OpenDash")
        assertThat(userAgent).contains("https://github.com/")
    }
}
