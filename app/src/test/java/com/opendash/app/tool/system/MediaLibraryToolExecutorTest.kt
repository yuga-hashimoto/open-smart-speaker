package com.opendash.app.tool.system

import com.google.common.truth.Truth.assertThat
import com.opendash.app.tool.ToolCall
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MediaLibraryToolExecutorTest {

    private lateinit var executor: MediaLibraryToolExecutor
    private lateinit var provider: MediaLibraryProvider

    @BeforeEach
    fun setup() {
        provider = mockk(relaxed = true)
        executor = MediaLibraryToolExecutor(provider)
    }

    @Test
    fun `availableTools includes audio and video listings`() = runTest {
        assertThat(executor.availableTools().map { it.name })
            .containsExactly("list_recent_audio", "list_recent_videos")
    }

    @Test
    fun `list_recent_audio without permission errors`() = runTest {
        every { provider.hasAudioPermission() } returns false

        val result = executor.execute(
            ToolCall(id = "1", name = "list_recent_audio", arguments = emptyMap())
        )

        assertThat(result.success).isFalse()
        assertThat(result.error).contains("READ_MEDIA_AUDIO")
    }

    @Test
    fun `list_recent_audio returns formatted track`() = runTest {
        every { provider.hasAudioPermission() } returns true
        coEvery { provider.recentAudio(20) } returns listOf(
            AudioTrackInfo(
                uri = "content://audio/1",
                title = "Song",
                artist = "Artist",
                album = "Album",
                durationMs = 240_000L,
                sizeBytes = 5_000_000L
            )
        )

        val result = executor.execute(
            ToolCall(id = "2", name = "list_recent_audio", arguments = emptyMap())
        )

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("Song")
        assertThat(result.data).contains("Artist")
        assertThat(result.data).contains("\"duration_ms\":240000")
    }

    @Test
    fun `list_recent_videos without permission errors`() = runTest {
        every { provider.hasVideoPermission() } returns false

        val result = executor.execute(
            ToolCall(id = "3", name = "list_recent_videos", arguments = emptyMap())
        )

        assertThat(result.success).isFalse()
        assertThat(result.error).contains("READ_MEDIA_VIDEO")
    }

    @Test
    fun `list_recent_videos returns formatted entry`() = runTest {
        every { provider.hasVideoPermission() } returns true
        coEvery { provider.recentVideos(20) } returns listOf(
            VideoInfo(
                uri = "content://video/9",
                name = "trip.mp4",
                durationMs = 12_345L,
                sizeBytes = 99_999L,
                width = 1920,
                height = 1080,
                takenMs = 1_700_000_000_000L
            )
        )

        val result = executor.execute(
            ToolCall(id = "4", name = "list_recent_videos", arguments = emptyMap())
        )

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("trip.mp4")
        assertThat(result.data).contains("\"width\":1920")
    }
}
