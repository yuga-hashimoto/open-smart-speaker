package com.opendash.app.assistant.provider

import com.opendash.app.assistant.model.AssistantMessage
import com.opendash.app.assistant.model.AssistantSession
import com.opendash.app.tool.ToolSchema
import kotlinx.coroutines.flow.Flow

interface AssistantProvider {
    val id: String
    val displayName: String
    val capabilities: ProviderCapabilities

    suspend fun startSession(config: Map<String, String> = emptyMap()): AssistantSession

    suspend fun endSession(session: AssistantSession)

    suspend fun send(
        session: AssistantSession,
        messages: List<AssistantMessage>,
        tools: List<ToolSchema> = emptyList()
    ): AssistantMessage

    fun sendStreaming(
        session: AssistantSession,
        messages: List<AssistantMessage>,
        tools: List<ToolSchema> = emptyList()
    ): Flow<AssistantMessage.Delta>

    suspend fun isAvailable(): Boolean

    suspend fun latencyMs(): Long
}
