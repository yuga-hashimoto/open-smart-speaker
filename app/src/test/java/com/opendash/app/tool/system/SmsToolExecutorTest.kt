package com.opendash.app.tool.system

import com.google.common.truth.Truth.assertThat
import com.opendash.app.tool.ToolCall
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SmsToolExecutorTest {

    private lateinit var sender: SmsSender
    private lateinit var executor: SmsToolExecutor

    @BeforeEach
    fun setup() {
        sender = mockk(relaxed = true)
        executor = SmsToolExecutor(sender)
    }

    @Test
    fun `send_sms success returns sent true`() = runTest {
        coEvery { sender.send("+15551234", "hi") } returns SmsResult.Sent

        val result = executor.execute(
            ToolCall("1", "send_sms", mapOf("phone_number" to "+15551234", "message" to "hi"))
        )

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("\"sent\":true")
    }

    @Test
    fun `missing phone_number returns error`() = runTest {
        val result = executor.execute(
            ToolCall("2", "send_sms", mapOf("message" to "hi"))
        )

        assertThat(result.success).isFalse()
    }

    @Test
    fun `missing message returns error`() = runTest {
        val result = executor.execute(
            ToolCall("3", "send_sms", mapOf("phone_number" to "+15551234"))
        )

        assertThat(result.success).isFalse()
    }

    @Test
    fun `blank values return error`() = runTest {
        val result = executor.execute(
            ToolCall("4", "send_sms", mapOf("phone_number" to "", "message" to "hi"))
        )

        assertThat(result.success).isFalse()
    }

    @Test
    fun `no permission returns error with instructions`() = runTest {
        coEvery { sender.send(any(), any()) } returns SmsResult.NoPermission

        val result = executor.execute(
            ToolCall("5", "send_sms", mapOf("phone_number" to "+1", "message" to "m"))
        )

        assertThat(result.success).isFalse()
        assertThat(result.error).contains("permission")
    }

    @Test
    fun `failure returns error reason`() = runTest {
        coEvery { sender.send(any(), any()) } returns SmsResult.Failed("carrier error")

        val result = executor.execute(
            ToolCall("6", "send_sms", mapOf("phone_number" to "+1", "message" to "m"))
        )

        assertThat(result.success).isFalse()
        assertThat(result.error).contains("carrier error")
    }
}
