package com.opendash.app.assistant.session

import com.google.common.truth.Truth.assertThat
import com.opendash.app.assistant.model.AssistantMessage
import org.junit.jupiter.api.Test

class ConversationHistoryManagerTest {

    private fun user(text: String) = AssistantMessage.User(content = text)
    private fun assistant(text: String) = AssistantMessage.Assistant(content = text)
    private fun system(text: String) = AssistantMessage.System(content = text)

    @Test
    fun `add accumulates messages`() {
        val mgr = ConversationHistoryManager()
        mgr.add(user("hi"))
        mgr.add(assistant("hello"))
        assertThat(mgr.history).hasSize(2)
    }

    @Test
    fun `clear empties history`() {
        val mgr = ConversationHistoryManager()
        mgr.add(user("a"))
        mgr.add(user("b"))
        mgr.clear()
        assertThat(mgr.history).isEmpty()
    }

    @Test
    fun `history is defensively copied`() {
        val mgr = ConversationHistoryManager()
        mgr.add(user("one"))
        val snapshot = mgr.history
        mgr.add(user("two"))
        // The earlier snapshot should still reflect the moment it was taken.
        assertThat(snapshot).hasSize(1)
        assertThat(mgr.history).hasSize(2)
    }

    @Test
    fun `exceeds maxMessages trims keeping system + tail`() {
        val mgr = ConversationHistoryManager(maxMessages = 5)
        mgr.add(system("sys"))
        repeat(10) { mgr.add(user("user-$it")) }

        val out = mgr.history
        assertThat(out).hasSize(5)
        // System message is preserved at index 0.
        assertThat(out.first()).isInstanceOf(AssistantMessage.System::class.java)
        // Tail is the four most recent users.
        val tailContents = out.drop(1).map { (it as AssistantMessage.User).content }
        assertThat(tailContents).containsExactly("user-6", "user-7", "user-8", "user-9").inOrder()
    }

    @Test
    fun `exceeds character budget trims oldest non-system`() {
        // Budget 30 chars, no message-count trim until 50.
        val mgr = ConversationHistoryManager(maxMessages = 50, maxCharacters = 30)
        mgr.add(system("S"))
        // Each user msg is 10 chars.
        mgr.add(user("1234567890"))
        mgr.add(user("ABCDEFGHIJ"))
        mgr.add(user("KLMNOPQRST"))
        mgr.add(user("UVWXYZabcd"))

        // Total would be 1 + 40 = 41 chars → must trim to fit <=30 plus system.
        val totalChars = mgr.history.sumOf {
            when (it) {
                is AssistantMessage.User -> it.content.length
                is AssistantMessage.Assistant -> it.content.length
                is AssistantMessage.System -> it.content.length
                is AssistantMessage.ToolCallResult -> it.result.length
                is AssistantMessage.Delta -> it.contentDelta.length
            }
        }
        assertThat(totalChars).isAtMost(30)
        // System message preserved.
        assertThat(mgr.history.any { it is AssistantMessage.System }).isTrue()
        // Most-recent user message retained.
        val users = mgr.history.filterIsInstance<AssistantMessage.User>()
        assertThat(users.map { it.content }).contains("UVWXYZabcd")
    }

    @Test
    fun `system messages are always kept`() {
        val mgr = ConversationHistoryManager(maxMessages = 2)
        mgr.add(system("system-1"))
        mgr.add(system("system-2"))
        mgr.add(user("hi"))
        mgr.add(user("there"))
        mgr.add(user("again"))

        val systems = mgr.history.filterIsInstance<AssistantMessage.System>()
        // Both system messages must stay.
        assertThat(systems.map { it.content }).containsExactly("system-1", "system-2")
    }
}
