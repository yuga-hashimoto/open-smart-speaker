package com.opendash.app.assistant.provider.embedded

data class EmbeddedLlmConfig(
    val modelPath: String = "",
    val contextSize: Int = 1024,
    val threads: Int = 4, // performance cores only (off-grid-mobile-ai recommendation)
    val gpuLayers: Int = 0, // Android CPU only (off-grid-mobile-ai default)
    val temperature: Float = 0.7f,
    val maxTokens: Int = 128,
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT
) {
    companion object {
        const val DEFAULT_SYSTEM_PROMPT = """You are an AI assistant running on a smart speaker tablet. You help users control their smart home devices and answer questions.

You can call tools to interact with devices. When you need to perform an action, respond with a tool call in JSON format. After receiving a tool result, use it to give the user a helpful response.

Be concise and conversational — users are speaking to you, not reading long text. Respond in the same language the user speaks."""

        /**
         * Build a config tuned to the device's actual hardware profile.
         * Use this in production code instead of the raw data class defaults
         * so low-RAM devices don't pick an overly-large context / GPU layer
         * count that would OOM or hang.
         */
        fun forHardware(
            modelPath: String,
            profile: HardwareProfile,
            systemPrompt: String = DEFAULT_SYSTEM_PROMPT
        ): EmbeddedLlmConfig {
            val contextSize = when (profile.tier) {
                HardwareProfile.MemoryTier.LOW_3_4 -> 512
                HardwareProfile.MemoryTier.MID_4_6 -> 1024
                HardwareProfile.MemoryTier.HIGH_6_8 -> 2048
                HardwareProfile.MemoryTier.FLAGSHIP_8_12 -> 4096
                HardwareProfile.MemoryTier.TOP_12_PLUS -> 8192
            }
            return EmbeddedLlmConfig(
                modelPath = modelPath,
                contextSize = contextSize,
                threads = profile.recommendedThreads,
                gpuLayers = profile.suggestedGpuLayers,
                systemPrompt = systemPrompt
            )
        }
    }
}
