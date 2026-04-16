package com.opensmarthome.speaker.tool.system

import com.google.common.truth.Truth.assertThat
import com.opensmarthome.speaker.tool.ToolCall
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CameraToolExecutorTest {

    private lateinit var holder: CameraProviderHolder
    private lateinit var executor: CameraToolExecutor

    @BeforeEach
    fun setup() {
        holder = CameraProviderHolder()
        executor = CameraToolExecutor(holder)
    }

    @Test
    fun `availableTools has take_photo`() = runTest {
        assertThat(executor.availableTools().map { it.name }).containsExactly("take_photo")
    }

    @Test
    fun `default NoOp provider returns not-ready error`() = runTest {
        val result = executor.execute(ToolCall("1", "take_photo", emptyMap()))

        assertThat(result.success).isFalse()
        assertThat(result.error).contains("Camera not ready")
    }

    @Test
    fun `capture success returns metadata`() = runTest {
        holder.setProvider(object : CameraProvider {
            override suspend fun capture(request: CaptureRequest): CaptureResult =
                CaptureResult.Success(imageBytes = byteArrayOf(1, 2, 3), mimeType = "image/jpeg")
            override fun isReady(): Boolean = true
        })

        val result = executor.execute(ToolCall("2", "take_photo", mapOf("facing" to "front")))

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("image/jpeg")
        assertThat(result.data).contains("\"size_bytes\":3")
    }

    @Test
    fun `capture failed returns reason`() = runTest {
        holder.setProvider(object : CameraProvider {
            override suspend fun capture(request: CaptureRequest): CaptureResult =
                CaptureResult.Failed("device busy")
            override fun isReady(): Boolean = true
        })

        val result = executor.execute(ToolCall("3", "take_photo", emptyMap()))

        assertThat(result.success).isFalse()
        assertThat(result.error).contains("device busy")
    }

    @Test
    fun `facing parameter front is accepted`() = runTest {
        var capturedFacing: CaptureRequest.Facing? = null
        holder.setProvider(object : CameraProvider {
            override suspend fun capture(request: CaptureRequest): CaptureResult {
                capturedFacing = request.facing
                return CaptureResult.Success(byteArrayOf(), "image/jpeg")
            }
            override fun isReady(): Boolean = true
        })

        executor.execute(ToolCall("4", "take_photo", mapOf("facing" to "front")))

        assertThat(capturedFacing).isEqualTo(CaptureRequest.Facing.FRONT)
    }

    @Test
    fun `holder clear restores NoOp`() {
        holder.setProvider(object : CameraProvider {
            override suspend fun capture(request: CaptureRequest) =
                CaptureResult.Success(byteArrayOf(), "image/jpeg")
            override fun isReady(): Boolean = true
        })
        assertThat(holder.current().isReady()).isTrue()

        holder.clear()
        assertThat(holder.current().isReady()).isFalse()
    }
}
