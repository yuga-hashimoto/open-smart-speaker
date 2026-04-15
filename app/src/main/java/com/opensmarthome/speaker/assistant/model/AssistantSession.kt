package com.opensmarthome.speaker.assistant.model

import java.util.UUID

data class AssistantSession(
    val id: String = UUID.randomUUID().toString(),
    val providerId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val metadata: Map<String, String> = emptyMap()
)
