package com.opensmarthome.speaker.tool.system

import com.google.common.truth.Truth.assertThat
import com.opensmarthome.speaker.tool.ToolCall
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NotificationToolExecutorTest {

    private lateinit var executor: NotificationToolExecutor
    private lateinit var provider: NotificationProvider

    @BeforeEach
    fun setup() {
        provider = mockk(relaxed = true)
        executor = NotificationToolExecutor(provider)
    }

    @Test
    fun `availableTools includes notification tools`() = runTest {
        val tools = executor.availableTools()
        val names = tools.map { it.name }

        assertThat(names).contains("list_notifications")
        assertThat(names).contains("clear_notifications")
    }

    @Test
    fun `list_notifications returns empty array when no notifications`() = runTest {
        every { provider.isListenerEnabled() } returns true
        coEvery { provider.listNotifications() } returns emptyList()

        val result = executor.execute(
            ToolCall(id = "1", name = "list_notifications", arguments = emptyMap())
        )

        assertThat(result.success).isTrue()
        assertThat(result.data).isEqualTo("[]")
    }

    @Test
    fun `list_notifications returns notification details`() = runTest {
        every { provider.isListenerEnabled() } returns true
        coEvery { provider.listNotifications() } returns listOf(
            NotificationInfo(
                packageName = "com.android.email",
                appName = "Email",
                title = "New message",
                text = "Hello from John",
                postedAtMs = 1700000000000L,
                key = "key1"
            )
        )

        val result = executor.execute(
            ToolCall(id = "2", name = "list_notifications", arguments = emptyMap())
        )

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("Email")
        assertThat(result.data).contains("New message")
        assertThat(result.data).contains("Hello from John")
    }

    @Test
    fun `list_notifications without permission returns error`() = runTest {
        every { provider.isListenerEnabled() } returns false

        val result = executor.execute(
            ToolCall(id = "3", name = "list_notifications", arguments = emptyMap())
        )

        assertThat(result.success).isFalse()
        assertThat(result.error).contains("permission")
    }

    @Test
    fun `clear_notifications clears all when no package given`() = runTest {
        every { provider.isListenerEnabled() } returns true
        coEvery { provider.clearAll() } returns true

        val result = executor.execute(
            ToolCall(id = "4", name = "clear_notifications", arguments = emptyMap())
        )

        assertThat(result.success).isTrue()
    }

    @Test
    fun `clear_notifications clears specific package`() = runTest {
        every { provider.isListenerEnabled() } returns true
        coEvery { provider.clear("com.android.email") } returns true

        val result = executor.execute(
            ToolCall(id = "5", name = "clear_notifications", arguments = mapOf(
                "package_name" to "com.android.email"
            ))
        )

        assertThat(result.success).isTrue()
    }

    @Test
    fun `notification with special chars is escaped in JSON`() = runTest {
        every { provider.isListenerEnabled() } returns true
        coEvery { provider.listNotifications() } returns listOf(
            NotificationInfo(
                packageName = "com.test",
                appName = "Test",
                title = "Has \"quotes\"",
                text = "Has\nnewlines",
                postedAtMs = 0L,
                key = "k"
            )
        )

        val result = executor.execute(
            ToolCall(id = "6", name = "list_notifications", arguments = emptyMap())
        )

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("\\\"quotes\\\"")
        assertThat(result.data).doesNotContain("\n")
    }
}
