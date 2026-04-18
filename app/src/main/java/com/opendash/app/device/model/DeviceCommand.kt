package com.opendash.app.device.model

data class DeviceCommand(
    val deviceId: String,
    val action: String,
    val parameters: Map<String, Any?> = emptyMap()
)
