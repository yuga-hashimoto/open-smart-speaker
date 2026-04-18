package com.opensmarthome.speaker.data.location

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
 * Unit tests for [OpenMeteoCitySearchRepository]. The repository wraps
 * Open-Meteo's `geocoding-api.open-meteo.com/v1/search` endpoint so the
 * Settings picker can surface candidate cities in real time as the user
 * types. Tests exercise:
 *
 * 1. Happy path — a populated `results` array parses into CitySuggestion
 * 2. Empty query short-circuits without an HTTP call
 * 3. Empty `results` returns Success(emptyList), not failure
 * 4. HTTP failure propagates as Result.failure
 * 5. Missing optional fields (admin1) still parse without crash
 */
class CitySearchRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: OpenMeteoCitySearchRepository
    private val moshi = Moshi.Builder().build()

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = OpenMeteoCitySearchRepository(
            client = OkHttpClient(),
            moshi = moshi,
            apiUrl = server.url("/search").toString()
        )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `search returns parsed suggestions on success`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "results": [
                    {"name":"宗像","latitude":33.8,"longitude":130.55,"country":"Japan","admin1":"Fukuoka"},
                    {"name":"Munakata","latitude":33.81,"longitude":130.56,"country":"Japan","admin1":"Fukuoka Prefecture"}
                  ]
                }
                """.trimIndent()
            )
        )

        val result = repository.search("宗像", language = "ja")

        assertThat(result.isSuccess).isTrue()
        val list = result.getOrThrow()
        assertThat(list).hasSize(2)
        assertThat(list[0].name).isEqualTo("宗像")
        assertThat(list[0].admin1).isEqualTo("Fukuoka")
        assertThat(list[0].country).isEqualTo("Japan")
        assertThat(list[0].latitude).isEqualTo(33.8)
        assertThat(list[0].displayLabel).isEqualTo("宗像, Fukuoka, Japan")
    }

    @Test
    fun `search returns empty list when results missing`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("""{"generationtime_ms":0.1}""")
        )

        val result = repository.search("NoSuchPlace", language = "en")

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow()).isEmpty()
    }

    @Test
    fun `search short-circuits on blank query without calling server`() = runTest {
        val result = repository.search("   ", language = "ja")

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow()).isEmpty()
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `search returns failure on non-2xx response`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("oops"))

        val result = repository.search("Tokyo", language = "en")

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `search handles missing admin1 gracefully`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {"results":[{"name":"Atlantis","latitude":0.0,"longitude":0.0,"country":"Legend"}]}
                """.trimIndent()
            )
        )

        val result = repository.search("Atlantis", language = "en")

        assertThat(result.isSuccess).isTrue()
        val first = result.getOrThrow().first()
        assertThat(first.admin1).isNull()
        assertThat(first.displayLabel).isEqualTo("Atlantis, Legend")
    }

    @Test
    fun `search passes query and language as URL params`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("""{"results":[]}""")
        )

        repository.search("東京", language = "ja")

        val recorded = server.takeRequest()
        val path = recorded.path.orEmpty()
        assertThat(path).contains("language=ja")
        // Query is percent-encoded; we just ensure the `name` query arg is
        // there. MockWebServer lowercases nothing so any non-empty encoded
        // form is acceptable.
        assertThat(path).contains("name=")
        assertThat(path).contains("count=")
    }
}
