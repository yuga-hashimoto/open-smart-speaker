package com.opendash.app.tool.multiroom

import com.google.common.truth.Truth.assertThat
import com.opendash.app.multiroom.AnnouncementBroadcaster
import com.opendash.app.multiroom.BroadcastResult
import com.opendash.app.multiroom.SendOutcome
import com.opendash.app.tool.ToolCall
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class BroadcastTimerToolExecutorTest {

    private fun executor(broadcaster: AnnouncementBroadcaster) =
        BroadcastTimerToolExecutor(broadcaster)

    @Test
    fun `availableTools exposes broadcast_timer with seconds + optional label`() = runTest {
        val b = mockk<AnnouncementBroadcaster>()
        val schemas = executor(b).availableTools()
        assertThat(schemas).hasSize(1)
        val s = schemas.first()
        assertThat(s.name).isEqualTo("broadcast_timer")
        assertThat(s.parameters.keys).containsExactly("seconds", "label")
        assertThat(s.parameters["seconds"]?.required).isTrue()
        assertThat(s.parameters["label"]?.required).isFalse()
    }

    @Test
    fun `missing seconds returns failure without calling broadcaster`() = runTest {
        val b = mockk<AnnouncementBroadcaster>()
        val r = executor(b).execute(ToolCall("1", "broadcast_timer", emptyMap()))
        assertThat(r.success).isFalse()
        assertThat(r.error).contains("Missing seconds")
        coVerify(exactly = 0) { b.broadcastTimer(any(), any()) }
    }

    @Test
    fun `non-positive seconds returns failure`() = runTest {
        val b = mockk<AnnouncementBroadcaster>()
        val r = executor(b).execute(ToolCall("1", "broadcast_timer", mapOf("seconds" to 0)))
        assertThat(r.success).isFalse()
        assertThat(r.error).contains("positive")
    }

    @Test
    fun `unknown tool name returns failure`() = runTest {
        val b = mockk<AnnouncementBroadcaster>()
        val r = executor(b).execute(ToolCall("1", "something_else", mapOf("seconds" to 60)))
        assertThat(r.success).isFalse()
        assertThat(r.error).contains("Unknown tool")
    }

    @Test
    fun `happy path dispatches via broadcaster with seconds and label`() = runTest {
        val b = mockk<AnnouncementBroadcaster>()
        coEvery { b.broadcastTimer(300, "tea") } returns BroadcastResult(2, emptyList())
        val r = executor(b).execute(
            ToolCall("1", "broadcast_timer", mapOf("seconds" to 300, "label" to "tea"))
        )
        assertThat(r.success).isTrue()
        assertThat(r.data).contains("\"sent\":2")
        assertThat(r.data).contains("Timer set on 2 speakers")
        coVerify(exactly = 1) { b.broadcastTimer(300, "tea") }
    }

    @Test
    fun `seconds passed as Double (LLM JSON) is coerced to Int`() = runTest {
        val b = mockk<AnnouncementBroadcaster>()
        coEvery { b.broadcastTimer(300, null) } returns BroadcastResult(1, emptyList())
        val r = executor(b).execute(
            ToolCall("1", "broadcast_timer", mapOf("seconds" to 300.0))
        )
        assertThat(r.success).isTrue()
        coVerify(exactly = 1) { b.broadcastTimer(300, null) }
    }

    @Test
    fun `blank label is omitted and passed as null`() = runTest {
        val b = mockk<AnnouncementBroadcaster>()
        coEvery { b.broadcastTimer(120, null) } returns BroadcastResult(1, emptyList())
        val r = executor(b).execute(
            ToolCall("1", "broadcast_timer", mapOf("seconds" to 120, "label" to "   "))
        )
        assertThat(r.success).isTrue()
        coVerify(exactly = 1) { b.broadcastTimer(120, null) }
    }

    @Test
    fun `no peers reports sent=0 without failing the tool`() = runTest {
        val b = mockk<AnnouncementBroadcaster>()
        coEvery { b.broadcastTimer(any(), any()) } returns BroadcastResult(0, emptyList())
        val r = executor(b).execute(
            ToolCall("1", "broadcast_timer", mapOf("seconds" to 60))
        )
        assertThat(r.success).isTrue()
        assertThat(r.data).contains("No peers found")
    }

    @Test
    fun `missing secret surfaces as failure`() = runTest {
        val b = mockk<AnnouncementBroadcaster>()
        coEvery { b.broadcastTimer(any(), any()) } returns BroadcastResult(
            0,
            listOf("none" to SendOutcome.Other("no shared secret"))
        )
        val r = executor(b).execute(
            ToolCall("1", "broadcast_timer", mapOf("seconds" to 60))
        )
        assertThat(r.success).isFalse()
        assertThat(r.error).contains("no shared secret")
    }

    @Test
    fun `mixed success and failure reports reach count`() = runTest {
        val b = mockk<AnnouncementBroadcaster>()
        coEvery { b.broadcastTimer(any(), any()) } returns BroadcastResult(
            sentCount = 2,
            failures = listOf("peer-c" to SendOutcome.ConnectionRefused)
        )
        val r = executor(b).execute(
            ToolCall("1", "broadcast_timer", mapOf("seconds" to 60))
        )
        assertThat(r.success).isTrue()
        assertThat(r.data).contains("\"sent\":2")
        assertThat(r.data).contains("\"failed\":1")
    }
}
