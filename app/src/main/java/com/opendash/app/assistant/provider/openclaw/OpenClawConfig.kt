package com.opendash.app.assistant.provider.openclaw

data class OpenClawConfig(
    val gatewayUrl: String,
    val apiKey: String = "",
    val model: String = "",
    val reconnectDelayMs: Long = 3000L,
    val maxReconnectAttempts: Int = 10
)
