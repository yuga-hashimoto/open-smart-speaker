package com.opendash.app.assistant.context

import com.google.common.truth.Truth.assertThat
import com.opendash.app.assistant.model.AssistantMessage
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class ContextCompactorTest {

    @Test
    fun `history below threshold is returned unchanged`() = runTest {
        val compactor = ContextCompactor(maxMessages = 10, keepRecent = 5)
        val history = listOf(
            AssistantMessage.User(content = "hi"),
            AssistantMessage.Assistant(content = "hello")
        )

        val result = compactor.compact(history)

        assertThat(result.wasCompacted).isFalse()
        assertThat(result.messages).isEqualTo(history)
    }

    @Test
    fun `history over threshold gets compacted with naive summary`() = runTest {
        val compactor = ContextCompactor(maxMessages = 10, keepRecent = 3)
        val history = (1..15).flatMap {
            listOf(
                AssistantMessage.User(content = "Q$it"),
                AssistantMessage.Assistant(content = "A$it")
            )
        }

        val result = compactor.compact(history)

        assertThat(result.wasCompacted).isTrue()
        assertThat(result.removedCount).isGreaterThan(0)
        assertThat(result.messages.size).isLessThan(history.size)
        // First message should be a system summary
        val firstSystem = result.messages.firstOrNull { it is AssistantMessage.System }
        assertThat(firstSystem).isNotNull()
        assertThat((firstSystem as AssistantMessage.System).content).contains("summary")
    }

    @Test
    fun `initial system messages are preserved`() = runTest {
        val compactor = ContextCompactor(maxMessages = 10, keepRecent = 3)
        val system = AssistantMessage.System(content = "You are a helpful assistant.")
        val history = listOf(system) + (1..20).map { AssistantMessage.User(content = "msg$it") }

        val result = compactor.compact(history)

        assertThat(result.wasCompacted).isTrue()
        assertThat(result.messages[0]).isEqualTo(system)
    }

    @Test
    fun `recent messages are preserved in order`() = runTest {
        val compactor = ContextCompactor(maxMessages = 5, keepRecent = 3)
        val history = (1..10).map { AssistantMessage.User(content = "Q$it") }

        val result = compactor.compact(history)

        // Last 3 should be Q8, Q9, Q10
        val last3 = result.messages.takeLast(3).map { (it as AssistantMessage.User).content }
        assertThat(last3).containsExactly("Q8", "Q9", "Q10").inOrder()
    }

    @Test
    fun `uses summarizer when provided`() = runTest {
        val summarizer = mockk<ConversationSummarizer>()
        coEvery { summarizer.summarize(any()) } returns "Smart summary here"

        val compactor = ContextCompactor(maxMessages = 5, keepRecent = 2, summarizer = summarizer)
        val history = (1..10).map { AssistantMessage.User(content = "msg$it") }

        val result = compactor.compact(history)

        assertThat(result.wasCompacted).isTrue()
        val summaryMsg = result.messages.firstOrNull { it is AssistantMessage.System } as? AssistantMessage.System
        assertThat(summaryMsg?.content).contains("Smart summary here")
    }

    @Test
    fun `falls back to naive summary on summarizer error`() = runTest {
        val summarizer = mockk<ConversationSummarizer>()
        coEvery { summarizer.summarize(any()) } throws RuntimeException("LLM error")

        val compactor = ContextCompactor(maxMessages = 5, keepRecent = 2, summarizer = summarizer)
        val history = (1..10).map { AssistantMessage.User(content = "msg$it") }

        val result = compactor.compact(history)

        assertThat(result.wasCompacted).isTrue()
        // Should still compact using naive summary
        val summaryMsg = result.messages.firstOrNull { it is AssistantMessage.System }
        assertThat(summaryMsg).isNotNull()
    }
}
