package com.opendash.app.homeassistant.model

data class ServiceCall(
    val domain: String,
    val service: String,
    val entityId: String? = null,
    val data: Map<String, Any?> = emptyMap()
)

data class ServiceCallResult(
    val success: Boolean,
    val states: List<Entity> = emptyList()
)
