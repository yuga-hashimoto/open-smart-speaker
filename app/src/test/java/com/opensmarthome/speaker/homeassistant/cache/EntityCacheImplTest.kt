package com.opensmarthome.speaker.homeassistant.cache

import com.opensmarthome.speaker.homeassistant.client.HomeAssistantClient
import com.opensmarthome.speaker.homeassistant.client.HomeAssistantConfig
import com.opensmarthome.speaker.homeassistant.model.Entity
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EntityCacheImplTest {

    private lateinit var cache: EntityCacheImpl
    private lateinit var haClient: HomeAssistantClient
    private val config = HomeAssistantConfig("http://localhost:8123", "test-token", refreshIntervalMs = 60000)

    @BeforeEach
    fun setup() {
        haClient = mockk()
        cache = EntityCacheImpl(haClient, config)
    }

    @Test
    fun `refresh populates entities`() = runTest {
        val entities = listOf(
            Entity("light.a", "on", mapOf("friendly_name" to "Light A")),
            Entity("switch.b", "off", mapOf("friendly_name" to "Switch B"))
        )
        coEvery { haClient.getStates() } returns entities

        cache.refresh()

        assertEquals(2, cache.entities.value.size)
        assertNotNull(cache.getById("light.a"))
        assertNotNull(cache.getById("switch.b"))
    }

    @Test
    fun `getByDomain filters correctly`() = runTest {
        val entities = listOf(
            Entity("light.a", "on"),
            Entity("light.b", "off"),
            Entity("switch.c", "on")
        )
        coEvery { haClient.getStates() } returns entities

        cache.refresh()

        val lights = cache.getByDomain("light")
        assertEquals(2, lights.size)

        val switches = cache.getByDomain("switch")
        assertEquals(1, switches.size)
    }

    @Test
    fun `getById returns null for missing entity`() {
        assertNull(cache.getById("nonexistent.entity"))
    }

    @Test
    fun `refresh handles empty response`() = runTest {
        coEvery { haClient.getStates() } returns emptyList()

        cache.refresh()

        assertEquals(0, cache.entities.value.size)
    }

    @Test
    fun `refresh handles exception gracefully`() = runTest {
        coEvery { haClient.getStates() } throws RuntimeException("Network error")

        cache.refresh()

        assertEquals(0, cache.entities.value.size)
    }

    @Test
    fun `entity domain extraction works`() = runTest {
        val entities = listOf(Entity("climate.thermostat", "heat"))
        coEvery { haClient.getStates() } returns entities

        cache.refresh()

        val entity = cache.getById("climate.thermostat")
        assertNotNull(entity)
        assertEquals("climate", entity!!.domain)
    }
}
