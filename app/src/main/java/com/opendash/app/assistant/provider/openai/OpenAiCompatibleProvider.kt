package com.opendash.app.assistant.provider.openai

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
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class OpenAiCompatibleProvider(
    private val client: OkHttpClient,
    private val moshi: Moshi,
    private val config: OpenAiCompatibleConfig
) : AssistantProvider {

    override val id: String = "openai_compatible"
    override val displayName: String = "Local LLM"
    override val capabilities = ProviderCapabilities(
        supportsStreaming = true,
        supportsTools = true,
        maxContextTokens = config.maxTokens,
        modelName = config.model
    )

    private val parser = OpenAiStreamParser(moshi)
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun startSession(config: Map<String, String>): AssistantSession {
        return AssistantSession(providerId = id)
    }

    override suspend fun endSession(session: AssistantSession) {
        // Stateless REST - nothing to clean up
    }

    override suspend fun send(
        session: AssistantSession,
        messages: List<AssistantMessage>,
        tools: List<ToolSchema>
    ): AssistantMessage = suspendCancellableCoroutine { cont ->
        val body = buildRequestBody(messages, tools, stream = false)
        val request = buildHttpRequest(body)

        val call = client.newCall(request)
        cont.invokeOnCancellation { call.cancel() }

        try {
            val response = call.execute()
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                cont.resumeWithException(RuntimeException("HTTP ${response.code}: $responseBody"))
                return@suspendCancellableCoroutine
            }
            cont.resume(parser.parseFullResponse(responseBody))
        } catch (e: Exception) {
            cont.resumeWithException(e)
        }
    }

    override fun sendStreaming(
        session: AssistantSession,
        messages: List<AssistantMessage>,
        tools: List<ToolSchema>
    ): Flow<AssistantMessage.Delta> = flow {
        val body = buildRequestBody(messages, tools, stream = true)
        val request = buildHttpRequest(body)

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RuntimeException("HTTP ${response.code}: ${response.body?.string()}")
        }

        val reader = BufferedReader(InputStreamReader(response.body!!.byteStream()))
        reader.use {
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val parsed = parser.parseLine(line!!) ?: continue
                emit(parsed)
                if (parsed.finishReason != null) break
            }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun isAvailable(): Boolean {
        return try {
            val request = Request.Builder()
                .url("${config.baseUrl}/v1/models")
                .apply {
                    if (config.apiKey.isNotBlank()) {
                        addHeader("Authorization", "Bearer ${config.apiKey}")
                    }
                }
                .build()
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            Timber.d(e, "Provider unavailable: ${config.baseUrl}")
            false
        }
    }

    override suspend fun latencyMs(): Long {
        val start = java.lang.System.currentTimeMillis()
        isAvailable()
        return java.lang.System.currentTimeMillis() - start
    }

    private fun buildHttpRequest(body: String): Request {
        return Request.Builder()
            .url("${config.baseUrl}/v1/chat/completions")
            .post(body.toRequestBody(jsonMediaType))
            .apply {
                if (config.apiKey.isNotBlank()) {
                    addHeader("Authorization", "Bearer ${config.apiKey}")
                }
            }
            .build()
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildRequestBody(
        messages: List<AssistantMessage>,
        tools: List<ToolSchema>,
        stream: Boolean
    ): String {
        val msgList = mutableListOf<Map<String, Any>>()

        if (config.systemPrompt.isNotBlank()) {
            msgList.add(mapOf("role" to "system", "content" to config.systemPrompt))
        }

        messages.forEach { msg ->
            msgList.add(
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
            )
        }

        val payload = mutableMapOf<String, Any>(
            "model" to config.model,
            "messages" to msgList,
            "stream" to stream,
            "max_tokens" to config.maxTokens,
            "temperature" to config.temperature
        )

        if (tools.isNotEmpty()) {
            payload["tools"] = tools.map { tool ->
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to tool.name,
                        "description" to tool.description,
                        "parameters" to mapOf(
                            "type" to "object",
                            "properties" to tool.parameters.mapValues { (_, param) ->
                                mutableMapOf<String, Any>(
                                    "type" to param.type,
                                    "description" to param.description
                                ).apply {
                                    param.enum?.let { put("enum", it) }
                                }
                            },
                            "required" to tool.parameters.filter { it.value.required }.keys.toList()
                        )
                    )
                )
            }
        }

        return moshi.adapter(Map::class.java).toJson(payload as Map<Any?, Any?>) ?: "{}"
    }
}
