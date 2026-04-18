package com.opendash.app.tool.system

import com.google.common.truth.Truth.assertThat
import com.opendash.app.tool.ToolCall
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NotificationReplyToolExecutorTest {

    private lateinit var executor: NotificationReplyToolExecutor
    private lateinit var provider: NotificationProvider

    @BeforeEach
    fun setup() {
        provider = mockk(relaxed = true)
        executor = NotificationReplyToolExecutor(provider)
    }

    @Test
    fun `availableTools exposes reply_to_notification with key and text`() = runTest {
        val tools = executor.availableTools()
        assertThat(tools.map { it.name }).containsExactly("reply_to_notification")

        val schema = tools.single()
        assertThat(schema.parameters.keys).containsExactly("key", "text")
        assertThat(schema.parameters["key"]?.required).isTrue()
        assertThat(schema.parameters["text"]?.required).isTrue()
    }

    @Test
    fun `returns not-bound message when listener disabled`() = runTest {
        every { provider.isListenerEnabled() } returns false

        val result = executor.execute(
            ToolCall(
                id = "1",
                name = "reply_to_notification",
                arguments = mapOf("key" to "k", "text" to "hi")
            )
        )

        assertThat(result.success).isFalse()
        assertThat(result.error).contains("Notification access isn't enabled")
        assertThat(result.error).contains("Notification access")
    }

    @Test
    fun `missing key argument returns error`() = runTest {
        every { provider.isListenerEnabled() } returns true

        val result = executor.execute(
            ToolCall(
                id = "2",
                name = "reply_to_notification",
                arguments = mapOf("text" to "hello")
            )
        )

        assertThat(result.success).isFalse()
        assertThat(result.error).contains("Missing key")
    }

    @Test
    fun `missing text argument returns error`() = runTest {
        every { provider.isListenerEnabled() } returns true

        val result = executor.execute(
            ToolCall(
                id = "3",
                name = "reply_to_notification",
                arguments = mapOf("key" to "notif-key")
            )
        )

        assertThat(result.success).isFalse()
        assertThat(result.error).contains("Missing text")
    }

    @Test
    fun `blank key returns error`() = runTest {
        every { provider.isListenerEnabled() } returns true

        val result = executor.execute(
            ToolCall(
                id = "4",
                name = "reply_to_notification",
                arguments = mapOf("key" to "", "text" to "hi")
            )
        )

        assertThat(result.success).isFalse()
        assertThat(result.error).contains("key")
    }

    @Test
    fun `blank text returns error`() = runTest {
        every { provider.isListenerEnabled() } returns true

        val result = executor.execute(
            ToolCall(
                id = "5",
                name = "reply_to_notification",
                arguments = mapOf("key" to "k", "text" to "   ")
            )
        )

        assertThat(result.success).isFalse()
        assertThat(result.error).contains("text")
    }

    @Test
    fun `notification without reply action returns friendly error`() = runTest {
        every { provider.isListenerEnabled() } returns true
        coEvery { provider.replyToNotification("k", "hi") } returns ReplyOutcome.NoReplyAction

        val result = executor.execute(
            ToolCall(
                id = "6",
                name = "reply_to_notification",
                arguments = mapOf("key" to "k", "text" to "hi")
            )
        )

        assertThat(result.success).isFalse()
        assertThat(result.error).contains("doesn't have a reply action")
        assertThat(result.error).contains("open the app")
    }

    @Test
    fun `missing notification returns not-found error`() = runTest {
        every { provider.isListenerEnabled() } returns true
        coEvery { provider.replyToNotification("gone", "hi") } returns ReplyOutcome.NotFound

        val result = executor.execute(
            ToolCall(
                id = "7",
                name = "reply_to_notification",
                arguments = mapOf("key" to "gone", "text" to "hi")
            )
        )

        assertThat(result.success).isFalse()
        assertThat(result.error).contains("No active notification")
    }

    @Test
    fun `listener not connected returns retry hint`() = runTest {
        every { provider.isListenerEnabled() } returns true
        coEvery { provider.replyToNotification("k", "hi") } returns
            ReplyOutcome.ListenerNotConnected

        val result = executor.execute(
            ToolCall(
                id = "8",
                name = "reply_to_notification",
                arguments = mapOf("key" to "k", "text" to "hi")
            )
        )

        assertThat(result.success).isFalse()
        assertThat(result.error).contains("listener isn't connected")
    }

    @Test
    fun `send failure propagates reason`() = runTest {
        every { provider.isListenerEnabled() } returns true
        coEvery { provider.replyToNotification("k", "hi") } returns
            ReplyOutcome.Failed("pending intent cancelled")

        val result = executor.execute(
            ToolCall(
                id = "9",
                name = "reply_to_notification",
                arguments = mapOf("key" to "k", "text" to "hi")
            )
        )

        assertThat(result.success).isFalse()
        assertThat(result.error).isEqualTo("pending intent cancelled")
    }

    @Test
    fun `happy path returns success JSON`() = runTest {
        every { provider.isListenerEnabled() } returns true
        coEvery { provider.replyToNotification("k", "see you at 6") } returns ReplyOutcome.Sent

        val result = executor.execute(
            ToolCall(
                id = "10",
                name = "reply_to_notification",
                arguments = mapOf("key" to "k", "text" to "see you at 6")
            )
        )

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("\"sent\":true")
        coVerify(exactly = 1) { provider.replyToNotification("k", "see you at 6") }
    }

    @Test
    fun `unknown tool name returns error`() = runTest {
        every { provider.isListenerEnabled() } returns true

        val result = executor.execute(
            ToolCall(id = "11", name = "bogus", arguments = emptyMap())
        )

        assertThat(result.success).isFalse()
        assertThat(result.error).contains("Unknown tool")
    }
}
