package com.opensmarthome.speaker.homeassistant.client

import com.opensmarthome.speaker.homeassistant.model.Area
import com.opensmarthome.speaker.homeassistant.model.Entity
import com.opensmarthome.speaker.homeassistant.model.ServiceCall
import com.opensmarthome.speaker.homeassistant.model.ServiceCallResult
import com.squareup.moshi.Moshi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean

class HomeAssistantWebSocketClient(
    private val client: OkHttpClient,
    private val moshi: Moshi,
    private val config: HomeAssistantConfig,
    private val restClient: HomeAssistantRestClient
) : HomeAssistantClient {

    private var webSocket: WebSocket? = null
    private val messageId = AtomicInteger(1)
    private val authenticated = AtomicBoolean(false)
    private val stateChangeChannel = Channel<Entity>(Channel.BUFFERED)
    private var subscriptionId: Int? = null

    fun connect() {
        val wsUrl = config.baseUrl
            .replace("http://", "ws://")
            .replace("https://", "wss://") + "/api/websocket"

        val request = Request.Builder().url(wsUrl).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Timber.d("HA WebSocket connected")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(webSocket, text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                authenticated.set(false)
                Timber.d("HA WebSocket closed: $code")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                authenticated.set(false)
                Timber.e(t, "HA WebSocket failure")
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        authenticated.set(false)
    }

    private fun handleMessage(ws: WebSocket, text: String) {
        try {
            val json = JSONObject(text)
            when (json.optString("type")) {
                "auth_required" -> {
                    val authMsg = JSONObject().apply {
                        put("type", "auth")
                        put("access_token", config.token)
                    }
                    ws.send(authMsg.toString())
                }
                "auth_ok" -> {
                    authenticated.set(true)
                    Timber.d("HA WebSocket authenticated")
                    subscribeToStateChanges(ws)
                }
                "auth_invalid" -> {
                    Timber.e("HA WebSocket auth failed")
                    authenticated.set(false)
                }
                "event" -> {
                    handleEvent(json)
                }
                "result" -> {
                    handleResult(json)
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse HA WebSocket message")
        }
    }

    private fun subscribeToStateChanges(ws: WebSocket) {
        val id = messageId.getAndIncrement()
        subscriptionId = id
        val msg = JSONObject().apply {
            put("id", id)
            put("type", "subscribe_events")
            put("event_type", "state_changed")
        }
        ws.send(msg.toString())
    }

    @Suppress("UNCHECKED_CAST")
    private fun handleEvent(json: JSONObject) {
        try {
            val event = json.optJSONObject("event") ?: return
            val data = event.optJSONObject("data") ?: return
            val newState = data.optJSONObject("new_state") ?: return

            val entityId = newState.optString("entity_id", "")
            val state = newState.optString("state", "unknown")
            val attributes = newState.optJSONObject("attributes")
            val attrMap = mutableMapOf<String, Any?>()
            attributes?.keys()?.forEach { key ->
                attrMap[key] = attributes.opt(key)
            }

            val entity = Entity(
                entityId = entityId,
                state = state,
                attributes = attrMap,
                lastChanged = newState.optString("last_changed", ""),
                lastUpdated = newState.optString("last_updated", "")
            )
            stateChangeChannel.trySend(entity)
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse state change event")
        }
    }

    private fun handleResult(json: JSONObject) {
        val id = json.optInt("id")
        val success = json.optBoolean("success", false)
        if (id == subscriptionId && success) {
            Timber.d("Subscribed to state changes")
        }
    }

    // Delegate non-realtime operations to REST client
    override suspend fun getStates(): List<Entity> = restClient.getStates()
    override suspend fun getState(entityId: String): Entity = restClient.getState(entityId)
    override suspend fun callService(call: ServiceCall): ServiceCallResult = restClient.callService(call)
    override suspend fun getAreas(): List<Area> = restClient.getAreas()
    override fun stateChanges(): Flow<Entity> = stateChangeChannel.receiveAsFlow()
    override suspend fun isConnected(): Boolean = authenticated.get()
}
