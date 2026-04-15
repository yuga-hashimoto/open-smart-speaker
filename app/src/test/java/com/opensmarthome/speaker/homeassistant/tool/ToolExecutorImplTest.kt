package com.opensmarthome.speaker.homeassistant.tool

import com.opensmarthome.speaker.homeassistant.cache.EntityCache
import com.opensmarthome.speaker.homeassistant.client.HomeAssistantClient
import com.opensmarthome.speaker.homeassistant.model.Area
import com.opensmarthome.speaker.homeassistant.model.Entity
import com.opensmarthome.speaker.homeassistant.model.ServiceCallResult
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ToolExecutorImplTest {

    private lateinit var executor: ToolExecutorImpl
    private lateinit var haClient: HomeAssistantClient
    private lateinit var entityCache: EntityCache
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    @BeforeEach
    fun setup() {
        haClient = mockk()
        entityCache = mockk()
        every { entityCache.entities } returns MutableStateFlow(emptyMap())
        executor = ToolExecutorImpl(haClient, entityCache, moshi)
    }

    @Test
    fun `availableTools returns 4 tools`() = runTest {
        val tools = executor.availableTools()
        assertEquals(4, tools.size)
        assertEquals("get_entity_state", tools[0].name)
        assertEquals("get_entities_by_domain", tools[1].name)
        assertEquals("call_service", tools[2].name)
        assertEquals("get_areas", tools[3].name)
    }

    @Test
    fun `get_entity_state returns entity from cache`() = runTest {
        val entity = Entity("light.living_room", "on", mapOf("friendly_name" to "Living Room Light"))
        every { entityCache.getById("light.living_room") } returns entity

        val result = executor.execute(
            ToolCall("1", "get_entity_state", mapOf("entity_id" to "light.living_room"))
        )

        assertTrue(result.success)
        assertTrue(result.data.contains("light.living_room"))
    }

    @Test
    fun `get_entity_state falls back to client when not cached`() = runTest {
        every { entityCache.getById("sensor.temp") } returns null
        val entity = Entity("sensor.temp", "22.5", mapOf("friendly_name" to "Temperature"))
        coEvery { haClient.getState("sensor.temp") } returns entity

        val result = executor.execute(
            ToolCall("1", "get_entity_state", mapOf("entity_id" to "sensor.temp"))
        )

        assertTrue(result.success)
        assertTrue(result.data.contains("sensor.temp"))
    }

    @Test
    fun `get_entity_state fails with missing entity_id`() = runTest {
        val result = executor.execute(
            ToolCall("1", "get_entity_state", emptyMap())
        )

        assertFalse(result.success)
        assertEquals("Missing entity_id", result.error)
    }

    @Test
    fun `get_entities_by_domain returns filtered entities`() = runTest {
        val entities = listOf(
            Entity("light.a", "on", mapOf("friendly_name" to "Light A")),
            Entity("light.b", "off", mapOf("friendly_name" to "Light B"))
        )
        every { entityCache.getByDomain("light") } returns entities

        val result = executor.execute(
            ToolCall("1", "get_entities_by_domain", mapOf("domain" to "light"))
        )

        assertTrue(result.success)
        assertTrue(result.data.contains("light.a"))
        assertTrue(result.data.contains("light.b"))
    }

    @Test
    fun `call_service executes successfully`() = runTest {
        coEvery { haClient.callService(any()) } returns ServiceCallResult(success = true)

        val result = executor.execute(
            ToolCall("1", "call_service", mapOf(
                "domain" to "light",
                "service" to "turn_on",
                "entity_id" to "light.living_room"
            ))
        )

        assertTrue(result.success)
        assertTrue(result.data.contains("successfully"))
    }

    @Test
    fun `call_service fails when service call fails`() = runTest {
        coEvery { haClient.callService(any()) } returns ServiceCallResult(success = false)

        val result = executor.execute(
            ToolCall("1", "call_service", mapOf(
                "domain" to "light",
                "service" to "turn_on",
                "entity_id" to "light.living_room"
            ))
        )

        assertFalse(result.success)
    }

    @Test
    fun `call_service fails with missing domain`() = runTest {
        val result = executor.execute(
            ToolCall("1", "call_service", mapOf("service" to "turn_on"))
        )

        assertFalse(result.success)
        assertEquals("Missing domain", result.error)
    }

    @Test
    fun `get_areas returns area list`() = runTest {
        coEvery { haClient.getAreas() } returns listOf(
            Area("living_room", "Living Room"),
            Area("bedroom", "Bedroom")
        )

        val result = executor.execute(ToolCall("1", "get_areas", emptyMap()))
        assertTrue(result.success)
    }

    @Test
    fun `unknown tool returns error`() = runTest {
        val result = executor.execute(ToolCall("1", "unknown_tool", emptyMap()))
        assertFalse(result.success)
        assertTrue(result.error!!.contains("Unknown tool"))
    }

    @Test
    fun `exception during execution returns error result`() = runTest {
        every { entityCache.getById(any()) } returns null
        coEvery { haClient.getState(any()) } throws RuntimeException("Connection failed")

        val result = executor.execute(
            ToolCall("1", "get_entity_state", mapOf("entity_id" to "light.test"))
        )

        assertFalse(result.success)
        assertTrue(result.error!!.contains("Connection failed"))
    }
}
