package com.opensmarthome.speaker.tool.multiroom

import com.google.common.truth.Truth.assertThat
import com.opensmarthome.speaker.assistant.model.AssistantMessage
import com.opensmarthome.speaker.multiroom.AnnouncementBroadcaster
import com.opensmarthome.speaker.multiroom.SendOutcome
import com.opensmarthome.speaker.tool.ToolCall
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class HandoffToolExecutorTest {

    private fun makeExecutor(
        history: List<AssistantMessage> = emptyList(),
        outcome: SendOutcome = SendOutcome.Ok
    ): Pair<HandoffToolExecutor, AnnouncementBroadcaster> {
        val broadcaster = mockk<AnnouncementBroadcaster>()
        coEvery { broadcaster.handoffConversation(any(), any()) } returns outcome
        val exec = HandoffToolExecutor(
            broadcaster = broadcaster,
            historyProvider = { history }
        )
        return exec to broadcaster
    }

    @Test
    fun `availableTools exposes handoff_session with target param`() = runTest {
        val (exec, _) = makeExecutor()
        val schemas = exec.availableTools()
        assertThat(schemas).hasSize(1)
        assertThat(schemas[0].name).isEqualTo("handoff_session")
        assertThat(schemas[0].parameters.keys).containsExactly("target")
        assertThat(schemas[0].parameters["target"]?.required).isTrue()
    }

    @Test
    fun `execute happy path forwards last N messages to broadcaster and returns success`() = runTest {
        val history = (1..10).map { AssistantMessage.User(content = "m$it") }
        val (exec, broadcaster) = makeExecutor(history = history)

        val messagesSlot = slot<List<AssistantMessage>>()
        coEvery { broadcaster.handoffConversation(eq("kitchen"), capture(messagesSlot)) } returns SendOutcome.Ok

        val result = exec.execute(
            ToolCall(id = "c1", name = "handoff_session", arguments = mapOf("target" to "kitchen"))
        )

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("\"target\":\"kitchen\"")
        assertThat(result.data).contains("Moving to kitchen")
        assertThat(messagesSlot.captured).hasSize(HandoffToolExecutor.MAX_MESSAGES)
        // Should be the last MAX_MESSAGES of history.
        assertThat((messagesSlot.captured.last() as AssistantMessage.User).content).isEqualTo("m10")
        coVerify(exactly = 1) { broadcaster.handoffConversation("kitchen", any()) }
    }

    @Test
    fun `execute returns failure when target argument missing`() = runTest {
        val (exec, broadcaster) = makeExecutor()
        val result = exec.execute(
            ToolCall(id = "c2", name = "handoff_session", arguments = emptyMap())
        )
        assertThat(result.success).isFalse()
        assertThat(result.error).contains("target")
        coVerify(exactly = 0) { broadcaster.handoffConversation(any(), any()) }
    }

    @Test
    fun `execute surfaces broadcaster ConnectionRefused as friendly error`() = runTest {
        val (exec, _) = makeExecutor(outcome = SendOutcome.ConnectionRefused)
        val result = exec.execute(
            ToolCall(id = "c3", name = "handoff_session", arguments = mapOf("target" to "bedroom"))
        )
        assertThat(result.success).isFalse()
        assertThat(result.error).contains("bedroom")
        assertThat(result.error).contains("refused")
    }

    @Test
    fun `execute rejects unknown tool name`() = runTest {
        val (exec, _) = makeExecutor()
        val result = exec.execute(
            ToolCall(id = "c4", name = "bogus", arguments = mapOf("target" to "kitchen"))
        )
        assertThat(result.success).isFalse()
        assertThat(result.error).contains("Unknown")
    }
}
