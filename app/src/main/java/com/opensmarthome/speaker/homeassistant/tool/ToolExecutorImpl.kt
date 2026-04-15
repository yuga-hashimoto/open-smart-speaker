package com.opensmarthome.speaker.homeassistant.tool

import com.opensmarthome.speaker.homeassistant.cache.EntityCache
import com.opensmarthome.speaker.homeassistant.client.HomeAssistantClient
import com.opensmarthome.speaker.homeassistant.model.ServiceCall
import com.squareup.moshi.Moshi
import timber.log.Timber

class ToolExecutorImpl(
    private val haClient: HomeAssistantClient,
    private val entityCache: EntityCache,
    private val moshi: Moshi
) : ToolExecutor {

    override suspend fun availableTools(): List<ToolSchema> = listOf(
        ToolSchema(
            name = "get_entity_state",
            description = "Get the current state of a Home Assistant entity",
            parameters = mapOf(
                "entity_id" to ToolParameter("string", "The entity ID (e.g. light.living_room)", required = true)
            )
        ),
        ToolSchema(
            name = "get_entities_by_domain",
            description = "List all entities of a specific domain",
            parameters = mapOf(
                "domain" to ToolParameter("string", "The domain (e.g. light, switch, climate)", required = true)
            )
        ),
        ToolSchema(
            name = "call_service",
            description = "Call a Home Assistant service to control a device",
            parameters = mapOf(
                "domain" to ToolParameter("string", "Service domain (e.g. light, switch)", required = true),
                "service" to ToolParameter("string", "Service name (e.g. turn_on, turn_off, toggle)", required = true),
                "entity_id" to ToolParameter("string", "Target entity ID", required = false),
                "data" to ToolParameter("object", "Additional service data as JSON", required = false)
            )
        ),
        ToolSchema(
            name = "get_areas",
            description = "List all areas/rooms in Home Assistant",
            parameters = emptyMap()
        )
    )

    @Suppress("UNCHECKED_CAST")
    override suspend fun execute(call: ToolCall): ToolResult {
        return try {
            when (call.name) {
                "get_entity_state" -> executeGetEntityState(call)
                "get_entities_by_domain" -> executeGetEntitiesByDomain(call)
                "call_service" -> executeCallService(call)
                "get_areas" -> executeGetAreas()
                else -> ToolResult(call.id, false, "", "Unknown tool: ${call.name}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Tool execution failed: ${call.name}")
            ToolResult(call.id, false, "", e.message ?: "Execution failed")
        }
    }

    private suspend fun executeGetEntityState(call: ToolCall): ToolResult {
        val entityId = call.arguments["entity_id"] as? String
            ?: return ToolResult(call.id, false, "", "Missing entity_id")

        val entity = entityCache.getById(entityId)
            ?: haClient.getState(entityId)

        val data = mapOf(
            "entity_id" to entity.entityId,
            "state" to entity.state,
            "friendly_name" to entity.friendlyName,
            "attributes" to entity.attributes
        )
        return ToolResult(call.id, true, toJson(data))
    }

    private fun executeGetEntitiesByDomain(call: ToolCall): ToolResult {
        val domain = call.arguments["domain"] as? String
            ?: return ToolResult(call.id, false, "", "Missing domain")

        val entities = entityCache.getByDomain(domain).map { entity ->
            mapOf(
                "entity_id" to entity.entityId,
                "state" to entity.state,
                "friendly_name" to entity.friendlyName
            )
        }
        return ToolResult(call.id, true, toJson(entities))
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun executeCallService(call: ToolCall): ToolResult {
        val domain = call.arguments["domain"] as? String
            ?: return ToolResult(call.id, false, "", "Missing domain")
        val service = call.arguments["service"] as? String
            ?: return ToolResult(call.id, false, "", "Missing service")
        val entityId = call.arguments["entity_id"] as? String
        val data = call.arguments["data"] as? Map<String, Any?> ?: emptyMap()

        val serviceCall = ServiceCall(
            domain = domain,
            service = service,
            entityId = entityId,
            data = data
        )

        val result = haClient.callService(serviceCall)
        return if (result.success) {
            ToolResult(call.id, true, "Service $domain.$service called successfully")
        } else {
            ToolResult(call.id, false, "", "Service call failed")
        }
    }

    private suspend fun executeGetAreas(): ToolResult {
        val areas = haClient.getAreas().map { mapOf("area_id" to it.areaId, "name" to it.name) }
        return ToolResult("", true, toJson(areas))
    }

    private fun toJson(data: Any): String {
        return try {
            moshi.adapter(Any::class.java).toJson(data) ?: "{}"
        } catch (e: Exception) {
            "{}"
        }
    }
}
