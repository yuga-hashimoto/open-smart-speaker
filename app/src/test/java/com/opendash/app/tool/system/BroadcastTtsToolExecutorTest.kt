package com.opendash.app.tool.system

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

class BroadcastTtsToolExecutorTest {

    private fun executor(broadcaster: AnnouncementBroadcaster) =
        BroadcastTtsToolExecutor(broadcaster)

    @Test
    fun `availableTools exposes broadcast_tts with text + language + group params`() = runTest {
        val b = mockk<AnnouncementBroadcaster>()
        val schemas = executor(b).availableTools()
        assertThat(schemas).hasSize(1)
        val s = schemas.first()
        assertThat(s.name).isEqualTo("broadcast_tts")
        assertThat(s.parameters.keys).containsExactly("text", "language", "group")
        assertThat(s.parameters["text"]?.required).isTrue()
        assertThat(s.parameters["language"]?.required).isFalse()
        assertThat(s.parameters["group"]?.required).isFalse()
    }

    @Test
    fun `missing text returns failure without calling broadcaster`() = runTest {
        val b = mockk<AnnouncementBroadcaster>()
        val r = executor(b).execute(ToolCall("1", "broadcast_tts", emptyMap()))
        assertThat(r.success).isFalse()
        assertThat(r.error).contains("Missing text")
    }

    @Test
    fun `blank text returns failure`() = runTest {
        val b = mockk<AnnouncementBroadcaster>()
        val r = executor(b).execute(ToolCall("1", "broadcast_tts", mapOf("text" to "   ")))
        assertThat(r.success).isFalse()
    }

    @Test
    fun `unknown tool name returns failure`() = runTest {
        val b = mockk<AnnouncementBroadcaster>()
        val r = executor(b).execute(ToolCall("1", "something_else", mapOf("text" to "hi")))
        assertThat(r.success).isFalse()
        assertThat(r.error).contains("Unknown tool")
    }

    @Test
    fun `happy path reports sentCount in the data blob`() = runTest {
        val b = mockk<AnnouncementBroadcaster>()
        coEvery { b.broadcastTts(any(), any()) } returns BroadcastResult(3, emptyList())
        val r = executor(b).execute(ToolCall("1", "broadcast_tts", mapOf("text" to "dinner ready")))
        assertThat(r.success).isTrue()
        assertThat(r.data).contains("\"sent\":3")
        assertThat(r.data).contains("\"failed\":0")
    }

    @Test
    fun `no peers reports sent=0 without failing tool`() = runTest {
        val b = mockk<AnnouncementBroadcaster>()
        coEvery { b.broadcastTts(any(), any()) } returns BroadcastResult(0, emptyList())
        val r = executor(b).execute(ToolCall("1", "broadcast_tts", mapOf("text" to "anyone home?")))
        assertThat(r.success).isTrue()
        assertThat(r.data).contains("No peers found")
    }

    @Test
    fun `missing secret reason surfaces as failure`() = runTest {
        val b = mockk<AnnouncementBroadcaster>()
        coEvery { b.broadcastTts(any(), any()) } returns BroadcastResult(
            0,
            listOf("none" to SendOutcome.Other("no shared secret"))
        )
        val r = executor(b).execute(ToolCall("1", "broadcast_tts", mapOf("text" to "hi")))
        assertThat(r.success).isFalse()
        assertThat(r.error).contains("no shared secret")
    }

    @Test
    fun `group argument routes to broadcastTtsToGroup`() = runTest {
        val b = mockk<AnnouncementBroadcaster>()
        coEvery { b.broadcastTtsToGroup("kitchen", "dinner", "en") } returns BroadcastResult(2, emptyList())

        val r = executor(b).execute(
            ToolCall("1", "broadcast_tts", mapOf("text" to "dinner", "group" to "kitchen"))
        )

        assertThat(r.success).isTrue()
        assertThat(r.data).contains("\"sent\":2")
        assertThat(r.data).contains("group: kitchen")
    }

    @Test
    fun `blank group falls through to unscoped broadcast`() = runTest {
        val b = mockk<AnnouncementBroadcaster>()
        coEvery { b.broadcastTts("hello", "en") } returns BroadcastResult(1, emptyList())

        val r = executor(b).execute(
            ToolCall("1", "broadcast_tts", mapOf("text" to "hello", "group" to "   "))
        )

        assertThat(r.success).isTrue()
        coVerify(exactly = 1) { b.broadcastTts("hello", "en") }
        coVerify(exactly = 0) { b.broadcastTtsToGroup(any(), any(), any()) }
    }

    @Test
    fun `unknown group surfaces as a friendly failure`() = runTest {
        val b = mockk<AnnouncementBroadcaster>()
        coEvery { b.broadcastTtsToGroup("ghost", any(), any()) } returns BroadcastResult(
            0,
            listOf("missing" to SendOutcome.Other("unknown group: ghost"))
        )
        val r = executor(b).execute(
            ToolCall("1", "broadcast_tts", mapOf("text" to "hi", "group" to "ghost"))
        )
        assertThat(r.success).isFalse()
        assertThat(r.error).contains("unknown group")
    }

    @Test
    fun `group with no reachable members returns helpful spoken message`() = runTest {
        val b = mockk<AnnouncementBroadcaster>()
        coEvery { b.broadcastTtsToGroup("kitchen", any(), any()) } returns BroadcastResult(0, emptyList())
        val r = executor(b).execute(
            ToolCall("1", "broadcast_tts", mapOf("text" to "hi", "group" to "kitchen"))
        )
        assertThat(r.success).isTrue()
        assertThat(r.data).contains("No speakers in group 'kitchen'")
    }

    @Test
    fun `mixed success and failure reports reach count`() = runTest {
        val b = mockk<AnnouncementBroadcaster>()
        coEvery { b.broadcastTts(any(), any()) } returns BroadcastResult(
            sentCount = 2,
            failures = listOf("peer-c" to SendOutcome.ConnectionRefused)
        )
        val r = executor(b).execute(ToolCall("1", "broadcast_tts", mapOf("text" to "hi")))
        assertThat(r.success).isTrue()
        assertThat(r.data).contains("\"sent\":2")
        assertThat(r.data).contains("\"failed\":1")
    }
}
