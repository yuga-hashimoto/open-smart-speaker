package com.opendash.app.assistant.provider.hermes

import com.opendash.app.assistant.model.AssistantMessage
import com.opendash.app.assistant.model.AssistantSession
import com.opendash.app.assistant.provider.AssistantProvider
import com.opendash.app.assistant.provider.ProviderCapabilities
import com.opendash.app.tool.ToolSchema
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID

/**
 * AssistantProvider that talks to a HermesAgent-compatible HTTP gateway.
 *
 * Request: POST {baseUrl}/chat with JSON { session_id, messages, tools }
 * Response: newline-delimited JSON stream of { delta, done }
 *
 * Used for hybrid / external-gateway setups so a heavy task can run off-device.
 * Keep minimal — users swap the URL to match their actual deployment.
 */
class HermesAgentProvider(
    private val client: OkHttpClient,
    private val moshi: Moshi,
    private val config: HermesAgentConfig
) : AssistantProvider {

    override val id: String = "hermes_agent"
    override val displayName: String = "HermesAgent"
    override val capabilities = ProviderCapabilities(
        supportsStreaming = true,
        supportsTools = true,
        maxContextTokens = config.maxContextTokens,
        modelName = config.model,
        isLocal = false
    )

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun startSession(config: Map<String, String>): AssistantSession =
        AssistantSession(providerId = id, id = UUID.randomUUID().toString())

    override suspend fun endSession(session: AssistantSession) {
        // Stateless HTTP — nothing server-side to tear down
    }

    override suspend fun send(
        session: AssistantSession,
        messages: List<AssistantMessage>,
        tools: List<ToolSchema>
    ): AssistantMessage {
        val buffer = StringBuilder()
        sendStreaming(session, messages, tools).collect { delta ->
            buffer.append(delta.contentDelta)
        }
        return AssistantMessage.Assistant(content = buffer.toString().trim())
    }

    override fun sendStreaming(
        session: AssistantSession,
        messages: List<AssistantMessage>,
        tools: List<ToolSchema>
    ): Flow<AssistantMessage.Delta> = flow {
        val body = buildRequestBody(session, messages, tools)
        val request = Request.Builder()
            .url(joinUrl(config.baseUrl, "/chat"))
            .apply {
                if (config.apiKey.isNotBlank()) {
                    addHeader("Authorization", "Bearer ${config.apiKey}")
                }
            }
            .post(body.toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("HermesAgent HTTP ${response.code}: ${response.message}")
            }
            val stream = response.body?.byteStream() ?: return@flow
            BufferedReader(InputStreamReader(stream)).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) continue
                    val delta = parseDelta(line)
                    emit(delta)
                    if (delta.finishReason != null) break
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun isAvailable(): Boolean = withTimeoutOrNull(5_000) {
        try {
            val req = Request.Builder().url(joinUrl(config.baseUrl, "/health")).get().build()
            client.newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            Timber.d(e, "HermesAgent health check failed")
            false
        }
    } ?: false

    override suspend fun latencyMs(): Long {
        val start = System.currentTimeMillis()
        isAvailable()
        return System.currentTimeMillis() - start
    }

    private fun buildRequestBody(
        session: AssistantSession,
        messages: List<AssistantMessage>,
        tools: List<ToolSchema>
    ): String {
        val msgList = messages.map { msg ->
            mapOf(
                "role" to roleOf(msg),
                "content" to contentOf(msg)
            )
        }
        val payload = mutableMapOf<String, Any>(
            "session_id" to session.id,
            "model" to config.model,
            "messages" to msgList,
            "stream" to true
        )
        if (tools.isNotEmpty()) {
            payload["tools"] = tools.map { mapOf("name" to it.name, "description" to it.description) }
        }
        return moshi.adapter(Map::class.java).toJson(payload) ?: "{}"
    }

    private fun parseDelta(line: String): AssistantMessage.Delta {
        return try {
            @Suppress("UNCHECKED_CAST")
            val map = moshi.adapter(Map::class.java).fromJson(line) as? Map<String, Any?>
                ?: return AssistantMessage.Delta(contentDelta = line)
            val content = map["delta"] as? String ?: ""
            val done = map["done"] as? Boolean ?: false
            AssistantMessage.Delta(
                contentDelta = content,
                finishReason = if (done) "stop" else null
            )
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse HermesAgent delta: $line")
            AssistantMessage.Delta(contentDelta = line)
        }
    }

    private fun roleOf(msg: AssistantMessage): String = when (msg) {
        is AssistantMessage.User -> "user"
        is AssistantMessage.Assistant -> "assistant"
        is AssistantMessage.System -> "system"
        is AssistantMessage.ToolCallResult -> "tool"
        is AssistantMessage.Delta -> "assistant"
    }

    private fun contentOf(msg: AssistantMessage): String = when (msg) {
        is AssistantMessage.User -> msg.content
        is AssistantMessage.Assistant -> msg.content
        is AssistantMessage.System -> msg.content
        is AssistantMessage.ToolCallResult -> msg.result
        is AssistantMessage.Delta -> msg.contentDelta
    }

    private fun joinUrl(base: String, path: String): String {
        val b = base.trimEnd('/')
        val p = if (path.startsWith("/")) path else "/$path"
        return "$b$p"
    }
}
