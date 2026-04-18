package com.opendash.app.assistant.provider.openclaw

import com.opendash.app.assistant.model.AssistantMessage
import com.opendash.app.assistant.model.AssistantSession
import com.opendash.app.assistant.model.ToolCallRequest
import com.opendash.app.assistant.provider.AssistantProvider
import com.opendash.app.assistant.provider.ProviderCapabilities
import com.opendash.app.tool.ToolSchema
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
        modelName = config.model.ifBlank { "openclaw" },
        isLocal = false
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
        val toolCalls = mutableListOf<ToolCallRequest>()
        var finished = false
        ws.messages.collect { raw ->
            if (finished) return@collect
            val parsed = parseResponse(raw)
            parsed.toolCallDelta?.let { toolCalls.add(it) }
            if (parsed.finishReason != null) {
                finished = true
                return@collect
            }
            responseBuilder.append(parsed.contentDelta)
        }
        return AssistantMessage.Assistant(
            content = responseBuilder.toString(),
            toolCalls = toolCalls
        )
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
            // Forward the full parameter schema so remote agents can validate args
            // and generate structured tool calls with the correct shape.
            payload["tools"] = tools.map { tool ->
                mapOf(
                    "name" to tool.name,
                    "description" to tool.description,
                    "parameters" to tool.parameters.mapValues { (_, p) ->
                        mapOf(
                            "type" to p.type,
                            "description" to p.description,
                            "required" to p.required,
                            "enum" to p.enum
                        ).filterValues { it != null }
                    }
                )
            }
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

            // Tool call format: {"tool_call": {"id": "...", "name": "...", "arguments": "..."}}
            @Suppress("UNCHECKED_CAST")
            val toolCallMap = map["tool_call"] as? Map<String, Any?>
            val toolCall = toolCallMap?.let {
                val name = it["name"] as? String ?: return@let null
                val args = it["arguments"]?.let { a ->
                    if (a is String) a else moshi.adapter(Any::class.java).toJson(a) ?: "{}"
                } ?: "{}"
                ToolCallRequest(
                    id = it["id"] as? String ?: "call_${System.currentTimeMillis()}",
                    name = name,
                    arguments = args
                )
            }

            AssistantMessage.Delta(
                contentDelta = content,
                toolCallDelta = toolCall,
                finishReason = if (done) "stop" else null
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse OpenClaw response")
            AssistantMessage.Delta(contentDelta = raw)
        }
    }
}
