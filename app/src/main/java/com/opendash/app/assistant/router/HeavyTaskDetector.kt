package com.opendash.app.assistant.router

import com.opendash.app.assistant.provider.ProviderCapabilities

/**
 * Heuristic that flags a user utterance as a "heavy task" better served by a
 * remote gateway than by the on-device model. Used by the router to pick a
 * provider when RoutingPolicy.Auto is selected and a remote option exists.
 *
 * The cost of getting this wrong both ways:
 *  - false positive (escalate simple ask) → wastes network + latency
 *  - false negative (keep heavy ask local) → slow response, bad output quality
 *
 * Deliberately conservative: heavy triggers require a clear signal (length,
 * explicit keyword, or a need the local model can't meet).
 */
object HeavyTaskDetector {

    /** Approx word-count threshold. Anything longer is likely complex / long-form. */
    private const val LONG_INPUT_WORDS = 80

    /** English keywords that strongly imply long-form / heavy reasoning. */
    private val heavyKeywords = listOf(
        "write an essay", "write an article", "write a story",
        "translate this document", "summarize this paper",
        "explain in detail", "step by step",
        "refactor", "optimize", "debug this code", "code review",
        // Multi-source reasoning / synthesis
        "compare and contrast", "deep dive", "in depth analysis",
        "research the", "do research on",
        // Code generation explicitly asking for full programs
        "write a function", "write a program", "implement an algorithm",
        // Multi-step planning / itinerary
        "plan a trip", "create an itinerary", "draft a proposal"
    )

    /** Japanese equivalents. */
    private val heavyKeywordsJa = listOf(
        "論文を要約", "記事を書いて", "エッセイを書いて", "物語を書いて",
        "詳しく説明", "翻訳してください", "コードレビュー",
        // Synthesis / analysis
        "比較して", "詳細な分析", "深く調べて",
        // Code generation
        "関数を書いて", "プログラムを書いて", "アルゴリズムを実装",
        // Planning
        "旅行計画", "提案書を作"
    )

    data class Decision(
        val escalate: Boolean,
        val reason: String
    )

    fun decide(
        userInput: String,
        localCapabilities: ProviderCapabilities
    ): Decision {
        val text = userInput.trim()
        if (text.isEmpty()) return Decision(false, "empty input")

        val wordCount = text.split(Regex("""\s+""")).size
        if (wordCount >= LONG_INPUT_WORDS) {
            return Decision(true, "long input ($wordCount words)")
        }

        val lower = text.lowercase()
        if (heavyKeywords.any { it in lower }) {
            return Decision(true, "heavy keyword match")
        }
        if (heavyKeywordsJa.any { it in text }) {
            return Decision(true, "heavy keyword match (ja)")
        }

        val asksForVision = ("image" in lower || "photo" in lower || "picture" in lower ||
            "画像" in text || "写真" in text)
        if (asksForVision && !localCapabilities.supportsVision) {
            return Decision(true, "vision request but local model lacks vision support")
        }

        return Decision(false, "fits on-device")
    }
}
