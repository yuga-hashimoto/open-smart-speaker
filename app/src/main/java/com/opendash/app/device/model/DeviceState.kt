package com.opendash.app.device.model

data class DeviceState(
    val deviceId: String,
    val isOn: Boolean? = null,
    val brightness: Float? = null,
    val temperature: Float? = null,
    val humidity: Float? = null,
    val position: Float? = null,
    val mediaTitle: String? = null,
    val attributes: Map<String, Any?> = emptyMap(),
    val lastUpdated: Long = java.lang.System.currentTimeMillis()
)
