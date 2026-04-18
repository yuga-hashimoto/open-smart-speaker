package com.opendash.app.assistant.context

import com.opendash.app.assistant.model.AssistantMessage
import timber.log.Timber

/**
 * Compacts conversation history when it exceeds a threshold.
 *
 * Inspired by off-grid-mobile-ai's contextCompaction pattern.
 * Budget allocation (approximate):
 *   - System prompt: 10%
 *   - Summary (compressed): 15%
 *   - Recent messages: 45%
 *   - Generation: 30%
 *
 * When the history exceeds MAX_MESSAGES, the oldest messages are
 * replaced with a single summary system message.
 */
class ContextCompactor(
    private val maxMessages: Int = 40,
    private val keepRecent: Int = 20,
    private val summarizer: ConversationSummarizer? = null
) {

    data class CompactResult(
        val messages: List<AssistantMessage>,
        val wasCompacted: Boolean,
        val removedCount: Int
    )

    /**
     * Returns a new list with old messages replaced by a summary if needed.
     * System messages at the start are always preserved.
     * If no summarizer is given, uses a naive text-based summary as fallback.
     */
    suspend fun compact(history: List<AssistantMessage>): CompactResult {
        if (history.size <= maxMessages) {
            return CompactResult(history, wasCompacted = false, removedCount = 0)
        }

        val initialSystem = history.takeWhile { it is AssistantMessage.System }
        val rest = history.drop(initialSystem.size)
        if (rest.size <= keepRecent) {
            return CompactResult(history, wasCompacted = false, removedCount = 0)
        }

        val toSummarize = rest.dropLast(keepRecent)
        val recent = rest.takeLast(keepRecent)

        val summaryText = try {
            summarizer?.summarize(toSummarize) ?: naiveSummary(toSummarize)
        } catch (e: Exception) {
            Timber.w(e, "Summarization failed, using naive summary")
            naiveSummary(toSummarize)
        }

        val summaryMessage = AssistantMessage.System(
            content = "[Previous conversation summary] $summaryText"
        )

        val newHistory = buildList {
            addAll(initialSystem)
            add(summaryMessage)
            addAll(recent)
        }

        Timber.d("Compacted conversation: ${history.size} → ${newHistory.size} messages")
        return CompactResult(
            messages = newHistory,
            wasCompacted = true,
            removedCount = toSummarize.size
        )
    }

    /**
     * Fallback summary when no summarizer is available.
     * Extracts user questions and key assistant responses as a bullet list.
     */
    private fun naiveSummary(messages: List<AssistantMessage>): String {
        val highlights = messages.mapNotNull { msg ->
            when (msg) {
                is AssistantMessage.User -> "User asked: ${msg.content.take(80)}"
                is AssistantMessage.Assistant ->
                    if (msg.content.isNotBlank()) "Assistant: ${msg.content.take(80)}" else null
                else -> null
            }
        }.take(10)

        return if (highlights.isEmpty()) {
            "Earlier messages omitted."
        } else {
            highlights.joinToString(" | ")
        }
    }
}

/**
 * Produces a compressed summary of a list of conversation messages.
 * Implementations may use the LLM itself, a smaller model, or heuristics.
 */
interface ConversationSummarizer {
    suspend fun summarize(messages: List<AssistantMessage>): String
}
