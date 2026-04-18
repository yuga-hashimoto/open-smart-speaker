package com.opendash.app.assistant.provider.openai

import com.opendash.app.assistant.model.AssistantMessage
import com.opendash.app.assistant.model.ToolCallRequest
import com.squareup.moshi.Moshi
import timber.log.Timber

class OpenAiStreamParser(private val moshi: Moshi) {

    fun parseLine(line: String): AssistantMessage.Delta? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed == "data: [DONE]") return null
        val json = if (trimmed.startsWith("data: ")) trimmed.removePrefix("data: ") else return null

        return try {
            @Suppress("UNCHECKED_CAST")
            val map = moshi.adapter(Map::class.java).fromJson(json) as? Map<String, Any?> ?: return null
            val choices = (map["choices"] as? List<*>)?.firstOrNull() as? Map<String, Any?> ?: return null
            val delta = choices["delta"] as? Map<String, Any?>
            val finishReason = choices["finish_reason"] as? String

            val content = delta?.get("content") as? String ?: ""
            val toolCallDelta = parseToolCallDelta(delta)

            AssistantMessage.Delta(
                contentDelta = content,
                toolCallDelta = toolCallDelta,
                finishReason = finishReason
            )
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse SSE line: $json")
            null
        }
    }

    fun parseFullResponse(json: String): AssistantMessage {
        return try {
            @Suppress("UNCHECKED_CAST")
            val map = moshi.adapter(Map::class.java).fromJson(json) as? Map<String, Any?>
                ?: return AssistantMessage.Assistant(content = "")
            val choices = (map["choices"] as? List<*>)?.firstOrNull() as? Map<String, Any?>
                ?: return AssistantMessage.Assistant(content = "")
            val message = choices["message"] as? Map<String, Any?>
                ?: return AssistantMessage.Assistant(content = "")

            val content = message["content"] as? String ?: ""
            val toolCalls = parseToolCalls(message)

            AssistantMessage.Assistant(
                content = content,
                toolCalls = toolCalls
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse full response")
            AssistantMessage.Assistant(content = "")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseToolCalls(message: Map<String, Any?>): List<ToolCallRequest> {
        val toolCalls = message["tool_calls"] as? List<*> ?: return emptyList()
        return toolCalls.mapNotNull { tc ->
            val call = tc as? Map<String, Any?> ?: return@mapNotNull null
            val function = call["function"] as? Map<String, Any?> ?: return@mapNotNull null
            ToolCallRequest(
                id = call["id"] as? String ?: "",
                name = function["name"] as? String ?: "",
                arguments = function["arguments"] as? String ?: "{}"
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseToolCallDelta(delta: Map<String, Any?>?): ToolCallRequest? {
        val toolCalls = delta?.get("tool_calls") as? List<*> ?: return null
        val firstCall = toolCalls.firstOrNull() as? Map<String, Any?> ?: return null
        val function = firstCall["function"] as? Map<String, Any?> ?: return null
        return ToolCallRequest(
            id = firstCall["id"] as? String ?: "",
            name = function["name"] as? String ?: "",
            arguments = function["arguments"] as? String ?: ""
        )
    }
}
