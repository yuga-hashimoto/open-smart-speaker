package com.opendash.app.device.model

data class CommandResult(
    val success: Boolean,
    val message: String? = null,
    val updatedState: DeviceState? = null
)
