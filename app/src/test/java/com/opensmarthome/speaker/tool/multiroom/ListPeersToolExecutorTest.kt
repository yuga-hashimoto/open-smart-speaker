package com.opensmarthome.speaker.tool.multiroom

import com.google.common.truth.Truth.assertThat
import com.opensmarthome.speaker.tool.ToolCall
import com.opensmarthome.speaker.util.DiscoveredSpeaker
import com.opensmarthome.speaker.util.MulticastDiscovery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class ListPeersToolExecutorTest {

    private fun exec(peers: List<DiscoveredSpeaker>): ListPeersToolExecutor {
        val d = mockk<MulticastDiscovery>()
        every { d.speakers } returns MutableStateFlow(peers)
        return ListPeersToolExecutor(d)
    }

    @Test
    fun `availableTools exposes list_peers with no params`() = runTest {
        val schemas = exec(emptyList()).availableTools()
        assertThat(schemas.single().name).isEqualTo("list_peers")
        assertThat(schemas.single().parameters).isEmpty()
    }

    @Test
    fun `empty peer list returns count zero`() = runTest {
        val r = exec(emptyList()).execute(ToolCall("1", "list_peers", emptyMap()))
        assertThat(r.success).isTrue()
        assertThat(r.data).contains("\"count\":0")
        assertThat(r.data).contains("\"peers\":[]")
    }

    @Test
    fun `resolved peer reports host and port`() = runTest {
        val r = exec(
            listOf(DiscoveredSpeaker("speaker-kitchen", "10.0.0.5", 8421))
        ).execute(ToolCall("1", "list_peers", emptyMap()))
        assertThat(r.data).contains("\"count\":1")
        assertThat(r.data).contains("\"name\":\"speaker-kitchen\"")
        assertThat(r.data).contains("\"host\":\"10.0.0.5\"")
        assertThat(r.data).contains("\"port\":8421")
    }

    @Test
    fun `unresolved peer omits host and port`() = runTest {
        val r = exec(
            listOf(DiscoveredSpeaker("speaker-unresolved"))
        ).execute(ToolCall("1", "list_peers", emptyMap()))
        assertThat(r.data).contains("\"name\":\"speaker-unresolved\"")
        assertThat(r.data).doesNotContain("host")
        assertThat(r.data).doesNotContain("port")
    }

    @Test
    fun `service names with special characters are escaped`() = runTest {
        val r = exec(
            listOf(DiscoveredSpeaker("speaker \"quoted\" \\slash"))
        ).execute(ToolCall("1", "list_peers", emptyMap()))
        assertThat(r.data).contains("\\\"quoted\\\"")
        assertThat(r.data).contains("\\\\slash")
    }

    @Test
    fun `unknown tool name returns failure`() = runTest {
        val r = exec(emptyList()).execute(ToolCall("1", "list_speakers", emptyMap()))
        assertThat(r.success).isFalse()
    }
}
