package com.opendash.app.assistant.provider.openai

data class OpenAiCompatibleConfig(
    val baseUrl: String = "http://localhost:8080",
    val apiKey: String = "",
    val model: String = "gemma-4-e2b",
    val maxTokens: Int = 4096,
    val temperature: Double = 0.7,
    val systemPrompt: String = "",
    val timeoutMs: Long = 60000L
)
