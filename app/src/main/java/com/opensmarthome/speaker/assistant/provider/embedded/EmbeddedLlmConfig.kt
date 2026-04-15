package com.opensmarthome.speaker.assistant.provider.embedded

data class EmbeddedLlmConfig(
    val modelPath: String = "",
    val contextSize: Int = 1024,
    val threads: Int = Runtime.getRuntime().availableProcessors().coerceIn(4, 8),
    val gpuLayers: Int = 0,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 128,
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT
) {
    companion object {
        const val DEFAULT_SYSTEM_PROMPT = """You are an AI assistant running on a smart speaker tablet. You help users control their smart home devices and answer questions.

You can call tools to interact with devices. When you need to perform an action, respond with a tool call in JSON format. After receiving a tool result, use it to give the user a helpful response.

Be concise and conversational — users are speaking to you, not reading long text. Respond in the same language the user speaks."""
    }
}
