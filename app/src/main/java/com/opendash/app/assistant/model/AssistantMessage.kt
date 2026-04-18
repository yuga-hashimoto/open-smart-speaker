package com.opendash.app.assistant.model

import java.util.UUID

sealed class AssistantMessage {
    abstract val id: String
    abstract val timestamp: Long

    data class User(
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = java.lang.System.currentTimeMillis(),
        val content: String,
        val attachments: List<MediaAttachment> = emptyList()
    ) : AssistantMessage()

    data class Assistant(
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = java.lang.System.currentTimeMillis(),
        val content: String,
        val toolCalls: List<ToolCallRequest> = emptyList()
    ) : AssistantMessage()

    data class System(
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = java.lang.System.currentTimeMillis(),
        val content: String
    ) : AssistantMessage()

    data class ToolCallResult(
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = java.lang.System.currentTimeMillis(),
        val callId: String,
        val result: String,
        val isError: Boolean = false
    ) : AssistantMessage()

    data class Delta(
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = java.lang.System.currentTimeMillis(),
        val contentDelta: String = "",
        val toolCallDelta: ToolCallRequest? = null,
        val finishReason: String? = null
    ) : AssistantMessage()
}

data class ToolCallRequest(
    val id: String,
    val name: String,
    val arguments: String
)
