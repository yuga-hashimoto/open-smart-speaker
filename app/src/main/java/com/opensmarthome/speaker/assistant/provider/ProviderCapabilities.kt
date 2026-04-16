package com.opensmarthome.speaker.assistant.provider

data class ProviderCapabilities(
    val supportsStreaming: Boolean,
    val supportsTools: Boolean,
    val maxContextTokens: Int,
    val modelName: String,
    val supportsVision: Boolean = false,
    val supportsAudio: Boolean = false,
    /**
     * True when the provider runs on-device (no internet required for inference).
     * Used by ErrorClassifier to avoid blaming the network for local failures.
     */
    val isLocal: Boolean = false
)
