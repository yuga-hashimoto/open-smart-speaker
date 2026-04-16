package com.opensmarthome.speaker.voice

import java.util.Locale

/**
 * Filler phrases spoken while the AI is processing.
 * Reference: OpenClaw Assistant OpenClawSession filler/wait phrases.
 *
 * Purpose: Keeps users engaged during long LLM inference (2-30s) so they
 * know the system is alive and working. Prevents awkward silence.
 *
 * Timing:
 * - INITIAL: Played 1.5s after user finishes speaking (acknowledges input)
 * - WAIT: Played every 5-8s after INITIAL while still processing
 */
object FillerPhrases {

    private val JAPANESE_INITIAL = listOf(
        "はい",
        "承知しました",
        "少々お待ちください",
        "確認しますね",
        "少し待ってください"
    )

    private val JAPANESE_WAIT = listOf(
        "もう少し考えます",
        "もう少しですね",
        "ちょっと考えています",
        "整理しています"
    )

    private val ENGLISH_INITIAL = listOf(
        "Okay",
        "Got it",
        "One moment",
        "Let me check",
        "Sure"
    )

    private val ENGLISH_WAIT = listOf(
        "Still thinking...",
        "Almost there...",
        "Just a moment more...",
        "Organizing my thoughts..."
    )

    fun initialPhrase(languageTag: String? = null): String {
        val isJa = isJapanese(languageTag)
        return (if (isJa) JAPANESE_INITIAL else ENGLISH_INITIAL).random()
    }

    fun waitPhrase(languageTag: String? = null): String {
        val isJa = isJapanese(languageTag)
        return (if (isJa) JAPANESE_WAIT else ENGLISH_WAIT).random()
    }

    private fun isJapanese(tag: String?): Boolean {
        if (tag.isNullOrBlank()) {
            return Locale.getDefault().language == "ja"
        }
        return tag.startsWith("ja", ignoreCase = true)
    }
}
