package com.opendash.app.homeassistant.client

data class HomeAssistantConfig(
    val baseUrl: String,
    val token: String,
    val refreshIntervalMs: Long = 30000L
)
