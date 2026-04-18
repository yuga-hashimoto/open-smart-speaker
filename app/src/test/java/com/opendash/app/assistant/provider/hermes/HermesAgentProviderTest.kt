package com.opendash.app.assistant.provider.hermes

import com.google.common.truth.Truth.assertThat
import com.opendash.app.assistant.model.AssistantMessage
import com.opendash.app.assistant.model.AssistantSession
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class HermesAgentProviderTest {

    private lateinit var server: MockWebServer
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val client = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .build()

    @BeforeEach
    fun setup() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun `send aggregates streaming deltas`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/x-ndjson")
                .setBody(
                    """
                    {"delta":"Hello"}
                    {"delta":" world"}
                    {"delta":"!","done":true}
                    """.trimIndent()
                )
        )

        val provider = HermesAgentProvider(
            client = client,
            moshi = moshi,
            config = HermesAgentConfig(baseUrl = server.url("/").toString())
        )
        val session = provider.startSession()
        val msg = provider.send(
            session = session,
            messages = listOf(AssistantMessage.User(content = "hi")),
            tools = emptyList()
        )

        assertThat(msg).isInstanceOf(AssistantMessage.Assistant::class.java)
        assertThat((msg as AssistantMessage.Assistant).content).isEqualTo("Hello world!")
    }

    @Test
    fun `isAvailable returns true on 200 health`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        val provider = HermesAgentProvider(
            client = client,
            moshi = moshi,
            config = HermesAgentConfig(baseUrl = server.url("/").toString())
        )
        assertThat(provider.isAvailable()).isTrue()
    }

    @Test
    fun `isAvailable returns false on non-2xx health`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        val provider = HermesAgentProvider(
            client = client,
            moshi = moshi,
            config = HermesAgentConfig(baseUrl = server.url("/").toString())
        )
        assertThat(provider.isAvailable()).isFalse()
    }

    @Test
    fun `send forwards apiKey as bearer`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"delta":"ok","done":true}""")
        )
        val provider = HermesAgentProvider(
            client = client,
            moshi = moshi,
            config = HermesAgentConfig(
                baseUrl = server.url("/").toString(),
                apiKey = "secret-token"
            )
        )
        val session = AssistantSession(providerId = "hermes_agent")
        provider.send(session, listOf(AssistantMessage.User(content = "hi")), emptyList())

        val recorded = server.takeRequest()
        assertThat(recorded.getHeader("Authorization")).isEqualTo("Bearer secret-token")
    }

    @Test
    fun `capabilities declare remote provider`() {
        val provider = HermesAgentProvider(
            client = client,
            moshi = moshi,
            config = HermesAgentConfig(baseUrl = "http://example")
        )
        assertThat(provider.capabilities.isLocal).isFalse()
        assertThat(provider.capabilities.supportsStreaming).isTrue()
        assertThat(provider.capabilities.supportsTools).isTrue()
    }
}
