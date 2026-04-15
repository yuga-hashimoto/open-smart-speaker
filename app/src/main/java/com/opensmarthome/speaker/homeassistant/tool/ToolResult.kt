package com.opensmarthome.speaker.homeassistant.tool

data class ToolResult(
    val callId: String,
    val success: Boolean,
    val data: String,
    val error: String? = null
)
