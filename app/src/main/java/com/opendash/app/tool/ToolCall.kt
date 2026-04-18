package com.opendash.app.tool

data class ToolCall(
    val id: String,
    val name: String,
    val arguments: Map<String, Any?>
)
