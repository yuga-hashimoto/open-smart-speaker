package com.opendash.app.homeassistant.model

data class Entity(
    val entityId: String,
    val state: String,
    val attributes: Map<String, Any?> = emptyMap(),
    val lastChanged: String = "",
    val lastUpdated: String = ""
) {
    val domain: String get() = entityId.substringBefore(".")
    val friendlyName: String get() = attributes["friendly_name"] as? String ?: entityId
}
