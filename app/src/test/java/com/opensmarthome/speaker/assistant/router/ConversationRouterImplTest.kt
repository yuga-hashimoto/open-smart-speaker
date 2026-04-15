package com.opensmarthome.speaker.assistant.router

import com.opensmarthome.speaker.assistant.model.AssistantMessage
import com.opensmarthome.speaker.assistant.model.AssistantSession
import com.opensmarthome.speaker.assistant.provider.AssistantProvider
import com.opensmarthome.speaker.assistant.provider.ProviderCapabilities
import com.opensmarthome.speaker.homeassistant.tool.ToolSchema
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ConversationRouterImplTest {

    private lateinit var router: ConversationRouterImpl

    @BeforeEach
    fun setup() {
        router = ConversationRouterImpl()
    }

    @Test
    fun `register and resolve provider with auto policy`() = runTest {
        val provider = createFakeProvider("test", available = true, latency = 100L)
        router.registerProvider(provider)

        val resolved = router.resolveProvider()
        assertEquals("test", resolved.id)
    }

    @Test
    fun `resolve throws when no providers available`() = runTest {
        assertThrows<NoAvailableProviderException> {
            router.resolveProvider()
        }
    }

    @Test
    fun `resolve skips unavailable providers in auto mode`() = runTest {
        val unavailable = createFakeProvider("down", available = false)
        val available = createFakeProvider("up", available = true)
        router.registerProvider(unavailable)
        router.registerProvider(available)

        val resolved = router.resolveProvider()
        assertEquals("up", resolved.id)
    }

    @Test
    fun `manual policy selects specific provider`() = runTest {
        val p1 = createFakeProvider("a", available = true)
        val p2 = createFakeProvider("b", available = true)
        router.registerProvider(p1)
        router.registerProvider(p2)

        router.selectProvider("b")
        val resolved = router.resolveProvider()
        assertEquals("b", resolved.id)
    }

    @Test
    fun `manual policy throws when provider unavailable`() = runTest {
        val provider = createFakeProvider("offline", available = false)
        router.registerProvider(provider)
        router.selectProvider("offline")

        assertThrows<NoAvailableProviderException> {
            router.resolveProvider()
        }
    }

    @Test
    fun `failover selects first available in order`() = runTest {
        val p1 = createFakeProvider("a", available = false)
        val p2 = createFakeProvider("b", available = true)
        val p3 = createFakeProvider("c", available = true)
        router.registerProvider(p1)
        router.registerProvider(p2)
        router.registerProvider(p3)
        router.setPolicy(RoutingPolicy.Failover(listOf("a", "b", "c")))

        val resolved = router.resolveProvider()
        assertEquals("b", resolved.id)
    }

    @Test
    fun `lowest latency selects fastest provider`() = runTest {
        val slow = createFakeProvider("slow", available = true, latency = 500L)
        val fast = createFakeProvider("fast", available = true, latency = 50L)
        router.registerProvider(slow)
        router.registerProvider(fast)
        router.setPolicy(RoutingPolicy.LowestLatency)

        val resolved = router.resolveProvider()
        assertEquals("fast", resolved.id)
    }

    @Test
    fun `unregister removes provider`() = runTest {
        val provider = createFakeProvider("temp", available = true)
        router.registerProvider(provider)
        assertEquals(1, router.availableProviders.value.size)

        router.unregisterProvider("temp")
        assertEquals(0, router.availableProviders.value.size)
    }

    @Test
    fun `unregister clears active provider if it was active`() = runTest {
        val provider = createFakeProvider("active", available = true)
        router.registerProvider(provider)
        router.selectProvider("active")
        assertNotNull(router.activeProvider.value)

        router.unregisterProvider("active")
        assertNull(router.activeProvider.value)
    }

    @Test
    fun `resolve updates active provider`() = runTest {
        val provider = createFakeProvider("p1", available = true)
        router.registerProvider(provider)
        assertNull(router.activeProvider.value)

        router.resolveProvider()
        assertEquals("p1", router.activeProvider.value?.id)
    }

    private fun createFakeProvider(
        id: String,
        available: Boolean = true,
        latency: Long = 100L
    ): AssistantProvider = object : AssistantProvider {
        override val id: String = id
        override val displayName: String = "Fake $id"
        override val capabilities = ProviderCapabilities(
            supportsStreaming = true, supportsTools = true, maxContextTokens = 4096, modelName = "fake"
        )
        override suspend fun startSession(config: Map<String, String>) = AssistantSession(providerId = id)
        override suspend fun endSession(session: AssistantSession) {}
        override suspend fun send(session: AssistantSession, messages: List<AssistantMessage>, tools: List<ToolSchema>) =
            AssistantMessage.Assistant(content = "response")
        override fun sendStreaming(session: AssistantSession, messages: List<AssistantMessage>, tools: List<ToolSchema>): Flow<AssistantMessage.Delta> = emptyFlow()
        override suspend fun isAvailable(): Boolean = available
        override suspend fun latencyMs(): Long = latency
    }
}
