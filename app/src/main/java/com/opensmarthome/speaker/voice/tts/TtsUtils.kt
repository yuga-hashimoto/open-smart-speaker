package com.opensmarthome.speaker.voice.tts

import android.content.Context
import android.content.Intent

object TtsUtils {

    private val REGEX_CODE_BLOCK_START = Regex("```.*\\n?")
    private val REGEX_CODE_BLOCK_END = Regex("```")
    private val REGEX_HEADER = Regex("^#{1,6}\\s+", RegexOption.MULTILINE)
    private val REGEX_BOLD = Regex("\\*\\*([^*]+)\\*\\*")
    private val REGEX_ITALIC = Regex("\\*([^*]+)\\*")
    private val REGEX_INLINE_CODE = Regex("`([^`]+)`")
    private val REGEX_LINK = Regex("\\[([^\\]]+)]\\([^)]+\\)")
    private val REGEX_IMAGE = Regex("!\\[([^\\]]*)]\\([^)]+\\)")
    private val REGEX_HR = Regex("^[-*_]{3,}$", RegexOption.MULTILINE)
    private val REGEX_BLOCKQUOTE = Regex("^>\\s?", RegexOption.MULTILINE)
    private val REGEX_BULLET = Regex("^\\s*[-*+]\\s+", RegexOption.MULTILINE)
    private val REGEX_NEWLINES = Regex("\n{3,}")

    fun stripMarkdownForSpeech(text: String): String {
        var result = text
        result = result.replace("<end_of_turn>", "")
        result = result.replace(REGEX_CODE_BLOCK_START, "")
        result = result.replace(REGEX_CODE_BLOCK_END, "")
        result = result.replace(REGEX_HEADER, "")
        result = result.replace(REGEX_BOLD, "$1")
        result = result.replace(REGEX_ITALIC, "$1")
        result = result.replace(REGEX_INLINE_CODE, "$1")
        result = result.replace(REGEX_LINK, "$1")
        result = result.replace(REGEX_IMAGE, "$1")
        result = result.replace(REGEX_HR, "")
        result = result.replace(REGEX_BLOCKQUOTE, "")
        result = result.replace(REGEX_BULLET, "")
        result = result.replace(REGEX_NEWLINES, "\n\n")
        return result.trim()
    }

    /**
     * Split a long string at sentence boundaries into chunks no longer than [maxChars].
     * Android TTS performance degrades with very long utterances. Breaking at sentence
     * boundaries keeps prosody natural.
     *
     * Reference: OpenClaw Assistant TTSUtils.splitTextForTTS
     */
    fun splitIntoChunks(text: String, maxChars: Int = 500): List<String> {
        if (text.length <= maxChars) return listOf(text)
        // Sentence terminators (JP + EN)
        val sentenceEnd = Regex("(?<=[。！？\\.!?])\\s*")
        val sentences = text.split(sentenceEnd).filter { it.isNotBlank() }
        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        for (s in sentences) {
            if (current.length + s.length > maxChars && current.isNotEmpty()) {
                chunks.add(current.toString().trim())
                current.clear()
            }
            current.append(s)
        }
        if (current.isNotEmpty()) chunks.add(current.toString().trim())
        return chunks
    }

    data class EngineInfo(
        val packageName: String,
        val label: String
    )

    fun getAvailableEngines(context: Context): List<EngineInfo> {
        val pm = context.packageManager
        val intent = Intent("android.intent.action.TTS_SERVICE")
        val services = pm.queryIntentServices(intent, 0)
        return services.map { resolveInfo ->
            val serviceInfo = resolveInfo.serviceInfo
            val label = serviceInfo.applicationInfo.loadLabel(pm).toString()
            EngineInfo(
                packageName = serviceInfo.packageName,
                label = label
            )
        }.distinctBy { it.packageName }
    }
}
