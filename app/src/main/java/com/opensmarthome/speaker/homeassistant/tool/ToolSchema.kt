package com.opensmarthome.speaker.homeassistant.tool

data class ToolSchema(
    val name: String,
    val description: String,
    val parameters: Map<String, ToolParameter>
)

data class ToolParameter(
    val type: String,
    val description: String,
    val required: Boolean = false,
    val enum: List<String>? = null
)
