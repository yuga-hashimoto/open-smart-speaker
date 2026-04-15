package com.opensmarthome.speaker.homeassistant.tool

data class ToolCall(
    val id: String,
    val name: String,
    val arguments: Map<String, Any?>
)
