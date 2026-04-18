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

class BroadcastCancelTimerToolExecutorTest {

    private fun executor(broadcaster: AnnouncementBroadcaster) =
        BroadcastCancelTimerToolExecutor(broadcaster)

    @Test
    fun `availableTools exposes broadcast_cancel_timer with optional id`() = runTest {
        val b = mockk<AnnouncementBroadcaster>()
        val schemas = executor(b).availableTools()
        assertThat(schemas).hasSize(1)
        val s = schemas.first()
        assertThat(s.name).isEqualTo("broadcast_cancel_timer")
        assertThat(s.parameters.keys).containsExactly("id")
        assertThat(s.parameters["id"]?.required).isFalse()
    }

    @Test
    fun `happy path with no id dispatches cancel-all broadcast and uses plural spoken`() = runTest {
        val b = mockk<AnnouncementBroadcaster>()
        coEvery { b.broadcastCancelTimer(null) } returns BroadcastResult(3, emptyList())
        val r = executor(b).execute(ToolCall("1", "broadcast_cancel_timer", emptyMap()))
        assertThat(r.success).isTrue()
        assertThat(r.data).contains("\"sent\":3")
        assertThat(r.data).contains("Timers cancelled on 3 speakers")
        coVerify(exactly = 1) { b.broadcastCancelTimer(null) }
    }

    @Test
    fun `specific id uses singular 'Timer cancelled' spoken phrase`() = runTest {
        val b = mockk<AnnouncementBroadcaster>()
        coEvery { b.broadcastCancelTimer("tid-7") } returns BroadcastResult(2, emptyList())
        val r = executor(b).execute(
            ToolCall("1", "broadcast_cancel_timer", mapOf("id" to "tid-7"))
        )
        assertThat(r.success).isTrue()
        assertThat(r.data).contains("Timer cancelled on 2 speakers")
        coVerify(exactly = 1) { b.broadcastCancelTimer("tid-7") }
    }

    @Test
    fun `singular count is reflected in spoken phrase`() = runTest {
        val b = mockk<AnnouncementBroadcaster>()
        coEvery { b.broadcastCancelTimer(null) } returns BroadcastResult(1, emptyList())
        val r = executor(b).execute(ToolCall("1", "broadcast_cancel_timer", emptyMap()))
        assertThat(r.success).isTrue()
        // Count of 1 should drop the plural 's' from 'speaker'
        assertThat(r.data).contains("Timers cancelled on 1 speaker.")
    }

    @Test
    fun `blank id is treated as no id`() = runTest {
        val b = mockk<AnnouncementBroadcaster>()
        coEvery { b.broadcastCancelTimer(null) } returns BroadcastResult(1, emptyList())
        val r = executor(b).execute(
            ToolCall("1", "broadcast_cancel_timer", mapOf("id" to "   "))
        )
        assertThat(r.success).isTrue()
        coVerify(exactly = 1) { b.broadcastCancelTimer(null) }
    }

    @Test
    fun `id value 'all' is treated as cancel-all`() = runTest {
        val b = mockk<AnnouncementBroadcaster>()
        coEvery { b.broadcastCancelTimer(null) } returns BroadcastResult(2, emptyList())
        val r = executor(b).execute(
            ToolCall("1", "broadcast_cancel_timer", mapOf("id" to "all"))
        )
        assertThat(r.success).isTrue()
        coVerify(exactly = 1) { b.broadcastCancelTimer(null) }
    }

    @Test
    fun `unknown tool name returns failure`() = runTest {
        val b = mockk<AnnouncementBroadcaster>()
        val r = executor(b).execute(ToolCall("1", "something_else", emptyMap()))
        assertThat(r.success).isFalse()
        assertThat(r.error).contains("Unknown tool")
    }

    @Test
    fun `no peers reports sent=0 without failing the tool`() = runTest {
        val b = mockk<AnnouncementBroadcaster>()
        coEvery { b.broadcastCancelTimer(any()) } returns BroadcastResult(0, emptyList())
        val r = executor(b).execute(ToolCall("1", "broadcast_cancel_timer", emptyMap()))
        assertThat(r.success).isTrue()
        assertThat(r.data).contains("No peers found")
    }

    @Test
    fun `missing secret surfaces as failure`() = runTest {
        val b = mockk<AnnouncementBroadcaster>()
        coEvery { b.broadcastCancelTimer(any()) } returns BroadcastResult(
            0,
            listOf("none" to SendOutcome.Other("no shared secret"))
        )
        val r = executor(b).execute(ToolCall("1", "broadcast_cancel_timer", emptyMap()))
        assertThat(r.success).isFalse()
        assertThat(r.error).contains("no shared secret")
    }

    @Test
    fun `mixed success and failure is reported with sent and failed counts`() = runTest {
        val b = mockk<AnnouncementBroadcaster>()
        coEvery { b.broadcastCancelTimer(any()) } returns BroadcastResult(
            sentCount = 2,
            failures = listOf("peer-c" to SendOutcome.ConnectionRefused)
        )
        val r = executor(b).execute(ToolCall("1", "broadcast_cancel_timer", emptyMap()))
        assertThat(r.success).isTrue()
        assertThat(r.data).contains("\"sent\":2")
        assertThat(r.data).contains("\"failed\":1")
    }
}
