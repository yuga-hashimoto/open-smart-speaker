package com.opensmarthome.speaker.tool.system

import com.google.common.truth.Truth.assertThat
import com.opensmarthome.speaker.tool.ToolCall
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PhotosToolExecutorTest {

    private lateinit var provider: PhotosProvider
    private lateinit var executor: PhotosToolExecutor

    @BeforeEach
    fun setup() {
        provider = mockk()
        executor = PhotosToolExecutor(provider)
    }

    @Test
    fun `availableTools has list_recent_photos`() = runTest {
        assertThat(executor.availableTools().map { it.name }).containsExactly("list_recent_photos")
    }

    @Test
    fun `list without permission returns error`() = runTest {
        every { provider.hasPermission() } returns false

        val result = executor.execute(
            ToolCall("1", "list_recent_photos", emptyMap())
        )

        assertThat(result.success).isFalse()
    }

    @Test
    fun `list returns photo metadata`() = runTest {
        every { provider.hasPermission() } returns true
        coEvery { provider.getLatest(10) } returns listOf(
            PhotoInfo("content://media/1", "IMG_001.jpg", 1700000000000L, 4000, 3000, 2_500_000L)
        )

        val result = executor.execute(
            ToolCall("2", "list_recent_photos", emptyMap())
        )

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("IMG_001.jpg")
        assertThat(result.data).contains("content://media/1")
        assertThat(result.data).contains("4000")
    }

    @Test
    fun `list uses limit parameter`() = runTest {
        every { provider.hasPermission() } returns true
        coEvery { provider.getLatest(5) } returns emptyList()

        val result = executor.execute(
            ToolCall("3", "list_recent_photos", mapOf("limit" to 5.0))
        )

        assertThat(result.success).isTrue()
        assertThat(result.data).isEqualTo("[]")
    }
}
