package com.opendash.app.homeassistant.client

import com.opendash.app.homeassistant.model.Area
import com.opendash.app.homeassistant.model.Entity
import com.opendash.app.homeassistant.model.ServiceCall
import com.opendash.app.homeassistant.model.ServiceCallResult
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber

class HomeAssistantRestClient(
    private val client: OkHttpClient,
    private val moshi: Moshi,
    private val config: HomeAssistantConfig
) : HomeAssistantClient {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun getStates(): List<Entity> = withContext(Dispatchers.IO) {
        val request = buildRequest("/api/states")
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            Timber.e("Failed to get states: ${response.code}")
            return@withContext emptyList()
        }
        parseEntityList(response.body?.string() ?: "[]")
    }

    override suspend fun getState(entityId: String): Entity = withContext(Dispatchers.IO) {
        val request = buildRequest("/api/states/$entityId")
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RuntimeException("Failed to get state for $entityId: ${response.code}")
        }
        parseEntity(response.body?.string() ?: "{}")
    }

    override suspend fun callService(call: ServiceCall): ServiceCallResult = withContext(Dispatchers.IO) {
        val payload = mutableMapOf<String, Any?>()
        call.entityId?.let { payload["entity_id"] = it }
        payload.putAll(call.data)

        val body = moshi.adapter(Map::class.java).toJson(payload)
            ?.toRequestBody(jsonMediaType) ?: "{}".toRequestBody(jsonMediaType)

        val request = Request.Builder()
            .url("${config.baseUrl}/api/services/${call.domain}/${call.service}")
            .post(body)
            .addHeader("Authorization", "Bearer ${config.token}")
            .addHeader("Content-Type", "application/json")
            .build()

        val response = client.newCall(request).execute()
        ServiceCallResult(success = response.isSuccessful)
    }

    override suspend fun getAreas(): List<Area> = withContext(Dispatchers.IO) {
        // HA REST API doesn't have a direct areas endpoint;
        // areas are accessed via WebSocket API. Return empty for REST-only mode.
        emptyList()
    }

    override fun stateChanges(): Flow<Entity> {
        // WebSocket subscription - implemented in HomeAssistantWebSocketClient
        return emptyFlow()
    }

    override suspend fun isConnected(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest("/api/")
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            Timber.d(e, "HA connection check failed")
            false
        }
    }

    private fun buildRequest(path: String): Request =
        Request.Builder()
            .url("${config.baseUrl}$path")
            .addHeader("Authorization", "Bearer ${config.token}")
            .addHeader("Content-Type", "application/json")
            .build()

    @Suppress("UNCHECKED_CAST")
    private fun parseEntityList(json: String): List<Entity> {
        return try {
            val list = moshi.adapter(List::class.java).fromJson(json) as? List<Map<String, Any?>>
                ?: return emptyList()
            list.map { mapToEntity(it) }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse entity list")
            emptyList()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseEntity(json: String): Entity {
        val map = moshi.adapter(Map::class.java).fromJson(json) as? Map<String, Any?>
            ?: throw RuntimeException("Failed to parse entity")
        return mapToEntity(map)
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapToEntity(map: Map<String, Any?>): Entity {
        return Entity(
            entityId = map["entity_id"] as? String ?: "",
            state = map["state"] as? String ?: "unknown",
            attributes = map["attributes"] as? Map<String, Any?> ?: emptyMap(),
            lastChanged = map["last_changed"] as? String ?: "",
            lastUpdated = map["last_updated"] as? String ?: ""
        )
    }
}
