package com.opendash.app.assistant.provider.hermes

/**
 * Configuration for [HermesAgentProvider].
 *
 * Targets HermesAgent-compatible HTTP gateways. The exact request shape is:
 *   POST {baseUrl}/chat
 *   { "session_id": String, "messages": [{role, content}], "tools": [...] }
 * Response is a streaming JSON array of deltas, one per line:
 *   {"delta":"partial text"}\n
 *   {"delta":"more text","done":true}\n
 *
 * Self-hosted endpoints that speak this protocol can plug in directly. If a
 * user's Hermes gateway speaks OpenAI-compatible chat completions instead,
 * they should register `OpenAiCompatibleProvider` with the same base URL.
 */
data class HermesAgentConfig(
    val baseUrl: String,
    val apiKey: String = "",
    val model: String = "hermes-agent",
    val maxContextTokens: Int = 32_000,
    val timeoutMs: Long = 60_000
)
