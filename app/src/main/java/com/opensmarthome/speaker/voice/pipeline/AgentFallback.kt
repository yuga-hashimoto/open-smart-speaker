package com.opensmarthome.speaker.voice.pipeline

/**
 * Human-friendly fallback phrases used by the agent loop when it can't
 * produce a direct answer (e.g. hit the tool-round safety cap).
 *
 * Kept tiny and language-aware so the user hears something in their TTS
 * language instead of silence. Japanese and English are the shipping set;
 * other languages fall back to English.
 */
internal object AgentFallback {

    /**
     * Phrase spoken after the agent loop hits [VoicePipeline]'s tool-round
     * cap. "時間がかかりすぎています" / "This is taking longer than expected"
     * communicates that the assistant is still alive but gave up this turn.
     */
    fun roundCapMessage(ttsLanguage: String?): String {
        val lower = ttsLanguage?.lowercase().orEmpty()
        return when {
            lower.startsWith("ja") ->
                "時間がかかりすぎています。もう一度、もう少し具体的に聞いてください。"
            else ->
                "This is taking longer than expected. Could you try rephrasing?"
        }
    }
}
