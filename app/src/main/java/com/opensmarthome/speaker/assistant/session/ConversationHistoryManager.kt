package com.opensmarthome.speaker.assistant.session

import com.opensmarthome.speaker.assistant.model.AssistantMessage
import timber.log.Timber

class ConversationHistoryManager(
    private val maxMessages: Int = 50,
    private val maxCharacters: Int = 13000
) {
    private val messages = mutableListOf<AssistantMessage>()

    val history: List<AssistantMessage> get() = messages.toList()

    fun add(message: AssistantMessage) {
        messages.add(message)
        trimIfNeeded()
    }

    fun clear() {
        messages.clear()
    }

    private fun trimIfNeeded() {
        // Trim by message count
        if (messages.size > maxMessages) {
            val systemMessages = messages.filterIsInstance<AssistantMessage.System>()
            val recentMessages = messages
                .filter { it !is AssistantMessage.System }
                .takeLast(maxMessages - systemMessages.size)
            messages.clear()
            messages.addAll(systemMessages + recentMessages)
            Timber.d("Trimmed by count to ${messages.size} messages")
            return
        }

        // Trim by total character length
        val totalChars = messages.sumOf { it.contentLength() }
        if (totalChars > maxCharacters) {
            val systemMessages = messages.filterIsInstance<AssistantMessage.System>()
            val nonSystemMessages = messages.filter { it !is AssistantMessage.System }

            messages.clear()
            messages.addAll(systemMessages)

            var charCount = systemMessages.sumOf { it.contentLength() }
            for (msg in nonSystemMessages.reversed()) {
                val msgLen = msg.contentLength()
                if (charCount + msgLen <= maxCharacters) {
                    messages.add(1.coerceAtMost(messages.size), msg)
                    charCount += msgLen
                }
            }
            // Re-sort by timestamp
            val sorted = messages.sortedBy { it.timestamp }
            messages.clear()
            messages.addAll(sorted)
            Timber.d("Trimmed by chars to ${messages.size} messages ($charCount chars)")
        }
    }

    private fun AssistantMessage.contentLength(): Int = when (this) {
        is AssistantMessage.User -> content.length
        is AssistantMessage.Assistant -> content.length
        is AssistantMessage.System -> content.length
        is AssistantMessage.ToolCallResult -> result.length
        is AssistantMessage.Delta -> contentDelta.length
    }
}
