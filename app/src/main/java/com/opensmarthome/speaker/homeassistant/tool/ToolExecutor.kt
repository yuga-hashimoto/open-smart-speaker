package com.opensmarthome.speaker.homeassistant.tool

interface ToolExecutor {
    suspend fun availableTools(): List<ToolSchema>
    suspend fun execute(call: ToolCall): ToolResult
}
