package com.opensmarthome.speaker.tool.multiroom

import com.google.common.truth.Truth.assertThat
import com.opensmarthome.speaker.multiroom.AnnouncementBroadcaster
import com.opensmarthome.speaker.multiroom.AnnouncementDispatcher
import com.opensmarthome.speaker.multiroom.BroadcastResult
import com.opensmarthome.speaker.multiroom.SendOutcome
import com.opensmarthome.speaker.tool.ToolCall
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class BroadcastAnnouncementToolExecutorTest {

    private fun executor(b: AnnouncementBroadcaster) = BroadcastAnnouncementToolExecutor(b)

    @Test
    fun `availableTools exposes broadcast_announcement with text and ttl_seconds params`() = runTest {
        val b = mockk<AnnouncementBroadcaster>()
        val schemas = executor(b).availableTools()
        assertThat(schemas).hasSize(1)
        val s = schemas.first()
        assertThat(s.name).isEqualTo("broadcast_announcement")
        assertThat(s.parameters.keys).containsExactly("text", "ttl_seconds")
        assertThat(s.parameters["text"]?.required).isTrue()
        assertThat(s.parameters["ttl_seconds"]?.required).isFalse()
    }

    @Test
    fun `missing text returns failure without calling broadcaster`() = runTest {
        val b = mockk<AnnouncementBroadcaster>()
        val r = executor(b).execute(ToolCall("1", "broadcast_announcement", emptyMap()))
        assertThat(r.success).isFalse()
        assertThat(r.error).contains("Missing text")
    }

    @Test
    fun `blank text returns failure`() = runTest {
        val b = mockk<AnnouncementBroadcaster>()
        val r = executor(b).execute(
            ToolCall("1", "broadcast_announcement", mapOf("text" to "   "))
        )
        assertThat(r.success).isFalse()
    }

    @Test
    fun `unknown tool name returns failure`() = runTest {
        val b = mockk<AnnouncementBroadcaster>()
        val r = executor(b).execute(ToolCall("1", "wrong", mapOf("text" to "hi")))
        assertThat(r.success).isFalse()
        assertThat(r.error).contains("Unknown tool")
    }

    @Test
    fun `default ttl is used when not provided`() = runTest {
        val b = mockk<AnnouncementBroadcaster>()
        coEvery {
            b.broadcastAnnouncement("dinner ready", AnnouncementBroadcaster.DEFAULT_ANNOUNCEMENT_TTL_SECONDS)
        } returns BroadcastResult(2, emptyList())

        val r = executor(b).execute(
            ToolCall("1", "broadcast_announcement", mapOf("text" to "dinner ready"))
        )
        assertThat(r.success).isTrue()
        assertThat(r.data).contains("\"sent\":2")
        assertThat(r.data).contains("\"ttl_seconds\":${AnnouncementBroadcaster.DEFAULT_ANNOUNCEMENT_TTL_SECONDS}")
        coVerify(exactly = 1) {
            b.broadcastAnnouncement("dinner ready", AnnouncementBroadcaster.DEFAULT_ANNOUNCEMENT_TTL_SECONDS)
        }
    }

    @Test
    fun `explicit ttl is forwarded to broadcaster`() = runTest {
        val b = mockk<AnnouncementBroadcaster>()
        coEvery { b.broadcastAnnouncement("hi", 120) } returns BroadcastResult(1, emptyList())
        val r = executor(b).execute(
            ToolCall("1", "broadcast_announcement", mapOf("text" to "hi", "ttl_seconds" to 120))
        )
        assertThat(r.success).isTrue()
        assertThat(r.data).contains("\"ttl_seconds\":120")
    }

    @Test
    fun `ttl below minimum is clamped before broadcaster call`() = runTest {
        val b = mockk<AnnouncementBroadcaster>()
        coEvery {
            b.broadcastAnnouncement("hi", AnnouncementDispatcher.TTL_MIN_SECONDS)
        } returns BroadcastResult(1, emptyList())
        val r = executor(b).execute(
            ToolCall("1", "broadcast_announcement", mapOf("text" to "hi", "ttl_seconds" to 1))
        )
        assertThat(r.success).isTrue()
        assertThat(r.data).contains("\"ttl_seconds\":${AnnouncementDispatcher.TTL_MIN_SECONDS}")
    }

    @Test
    fun `ttl above maximum is clamped before broadcaster call`() = runTest {
        val b = mockk<AnnouncementBroadcaster>()
        coEvery {
            b.broadcastAnnouncement("hi", AnnouncementDispatcher.TTL_MAX_SECONDS)
        } returns BroadcastResult(1, emptyList())
        val r = executor(b).execute(
            ToolCall("1", "broadcast_announcement", mapOf("text" to "hi", "ttl_seconds" to 99_999))
        )
        assertThat(r.success).isTrue()
        assertThat(r.data).contains("\"ttl_seconds\":${AnnouncementDispatcher.TTL_MAX_SECONDS}")
    }

    @Test
    fun `no peers reports sent=0 without failing tool`() = runTest {
        val b = mockk<AnnouncementBroadcaster>()
        coEvery { b.broadcastAnnouncement(any(), any()) } returns BroadcastResult(0, emptyList())
        val r = executor(b).execute(
            ToolCall("1", "broadcast_announcement", mapOf("text" to "anyone?"))
        )
        assertThat(r.success).isTrue()
        assertThat(r.data).contains("No peers found")
    }

    @Test
    fun `missing secret surfaces as failure`() = runTest {
        val b = mockk<AnnouncementBroadcaster>()
        coEvery { b.broadcastAnnouncement(any(), any()) } returns BroadcastResult(
            0,
            listOf("none" to SendOutcome.Other("no shared secret"))
        )
        val r = executor(b).execute(
            ToolCall("1", "broadcast_announcement", mapOf("text" to "hi"))
        )
        assertThat(r.success).isFalse()
        assertThat(r.error).contains("no shared secret")
    }

    @Test
    fun `mixed success and failure reports reach count`() = runTest {
        val b = mockk<AnnouncementBroadcaster>()
        coEvery { b.broadcastAnnouncement(any(), any()) } returns BroadcastResult(
            sentCount = 2,
            failures = listOf("peer-c" to SendOutcome.ConnectionRefused)
        )
        val r = executor(b).execute(
            ToolCall("1", "broadcast_announcement", mapOf("text" to "hi"))
        )
        assertThat(r.success).isTrue()
        assertThat(r.data).contains("\"sent\":2")
        assertThat(r.data).contains("\"failed\":1")
    }
}
