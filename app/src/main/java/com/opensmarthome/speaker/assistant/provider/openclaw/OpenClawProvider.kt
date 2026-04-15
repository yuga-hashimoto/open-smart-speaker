package com.opensmarthome.speaker.assistant.provider.openclaw

import com.opensmarthome.speaker.assistant.model.AssistantMessage
import com.opensmarthome.speaker.assistant.model.AssistantSession
import com.opensmarthome.speaker.assistant.provider.AssistantProvider
import com.opensmarthome.speaker.assistant.provider.ProviderCapabilities
import com.opensmarthome.speaker.homeassistant.tool.ToolSchema
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import timber.log.Timber

class OpenClawProvider(
    private val client: OkHttpClient,
    private val moshi: Moshi,
    private val config: OpenClawConfig
) : AssistantProvider {

    override val id: String = "openclaw"
    override val displayName: String = "OpenClaw"
    override val capabilities = ProviderCapabilities(
        supportsStreaming = true,
        supportsTools = true,
        maxContextTokens = 128000,
        modelName = config.model.ifBlank { "openclaw" }
    )

    private var webSocket: OpenClawWebSocket? = null

    override suspend fun startSession(config: Map<String, String>): AssistantSession {
        if (webSocket == null || !webSocket!!.isConnected()) {
            webSocket = OpenClawWebSocket(client, moshi, this.config)
            webSocket!!.connect()
        }
        return AssistantSession(providerId = id)
    }

    override suspend fun endSession(session: AssistantSession) {
        webSocket?.disconnect()
        webSocket = null
    }

    override suspend fun send(
        session: AssistantSession,
        messages: List<AssistantMessage>,
        tools: List<ToolSchema>
    ): AssistantMessage {
        val ws = webSocket ?: throw IllegalStateException("WebSocket not connected")
        val payload = buildRequestPayload(messages, tools)
        ws.send(payload)

        val responseBuilder = StringBuilder()
        var finished = false
        ws.messages.collect { raw ->
            if (finished) return@collect
            val parsed = parseResponse(raw)
            if (parsed.finishReason != null) {
                finished = true
                return@collect
            }
            responseBuilder.append(parsed.contentDelta)
        }
        return AssistantMessage.Assistant(content = responseBuilder.toString())
    }

    override fun sendStreaming(
        session: AssistantSession,
        messages: List<AssistantMessage>,
        tools: List<ToolSchema>
    ): Flow<AssistantMessage.Delta> = flow {
        val ws = webSocket ?: throw IllegalStateException("WebSocket not connected")
        val payload = buildRequestPayload(messages, tools)
        ws.send(payload)

        var finished = false
        ws.messages.collect { raw ->
            if (finished) return@collect
            val delta = parseResponse(raw)
            emit(delta)
            if (delta.finishReason != null) finished = true
        }
    }

    override suspend fun isAvailable(): Boolean {
        return webSocket?.isConnected() == true ||
            withTimeoutOrNull(5000) {
                val testWs = OpenClawWebSocket(client, moshi, config)
                testWs.connect()
                val connected = testWs.isConnected()
                testWs.disconnect()
                connected
            } ?: false
    }

    override suspend fun latencyMs(): Long {
        val start = java.lang.System.currentTimeMillis()
        isAvailable()
        return java.lang.System.currentTimeMillis() - start
    }

    private fun buildRequestPayload(
        messages: List<AssistantMessage>,
        tools: List<ToolSchema>
    ): String {
        val msgList = messages.map { msg ->
            mapOf(
                "role" to when (msg) {
                    is AssistantMessage.User -> "user"
                    is AssistantMessage.Assistant -> "assistant"
                    is AssistantMessage.System -> "system"
                    is AssistantMessage.ToolCallResult -> "tool"
                    is AssistantMessage.Delta -> "assistant"
                },
                "content" to when (msg) {
                    is AssistantMessage.User -> msg.content
                    is AssistantMessage.Assistant -> msg.content
                    is AssistantMessage.System -> msg.content
                    is AssistantMessage.ToolCallResult -> msg.result
                    is AssistantMessage.Delta -> msg.contentDelta
                }
            )
        }
        val payload = mutableMapOf<String, Any>("messages" to msgList)
        if (tools.isNotEmpty()) {
            payload["tools"] = tools.map { mapOf("name" to it.name, "description" to it.description) }
        }
        return try {
            moshi.adapter(Map::class.java).toJson(payload) ?: "{}"
        } catch (e: Exception) {
            Timber.e(e, "Failed to serialize payload")
            "{}"
        }
    }

    private fun parseResponse(raw: String): AssistantMessage.Delta {
        return try {
            @Suppress("UNCHECKED_CAST")
            val map = moshi.adapter(Map::class.java).fromJson(raw) as? Map<String, Any?> ?: emptyMap()
            val content = map["content"] as? String ?: ""
            val done = map["done"] as? Boolean ?: false
            AssistantMessage.Delta(
                contentDelta = content,
                finishReason = if (done) "stop" else null
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse OpenClaw response")
            AssistantMessage.Delta(contentDelta = raw)
        }
    }
}
