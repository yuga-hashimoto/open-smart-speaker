package com.opendash.app.tool.system

import com.google.common.truth.Truth.assertThat
import com.opendash.app.tool.ToolCall
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ScreenRecorderToolExecutorTest {

    private lateinit var holder: ScreenRecorderHolder
    private lateinit var executor: ScreenRecorderToolExecutor

    @BeforeEach
    fun setup() {
        holder = ScreenRecorderHolder()
        executor = ScreenRecorderToolExecutor(holder)
    }

    @Test
    fun `availableTools has start and stop`() = runTest {
        val names = executor.availableTools().map { it.name }
        assertThat(names).containsExactly("start_screen_recording", "stop_screen_recording")
    }

    @Test
    fun `default NoOp returns needs-user-consent`() = runTest {
        val result = executor.execute(
            ToolCall("1", "start_screen_recording", emptyMap())
        )
        assertThat(result.success).isFalse()
        assertThat(result.error).contains("consent")
    }

    @Test
    fun `stop without recording returns error`() = runTest {
        val result = executor.execute(
            ToolCall("2", "stop_screen_recording", emptyMap())
        )
        assertThat(result.success).isFalse()
    }

    @Test
    fun `start Started returns output path`() = runTest {
        holder.setRecorder(object : ScreenRecorder {
            override suspend fun start(request: RecordRequest) = StartResult.Started("/tmp/rec.mp4")
            override suspend fun stop() = StopResult.Stopped("/tmp/rec.mp4", 5000)
            override fun isRecording() = true
            override fun isReady() = true
        })

        val result = executor.execute(
            ToolCall("3", "start_screen_recording", mapOf("max_duration_sec" to 10.0))
        )
        assertThat(result.success).isTrue()
        assertThat(result.data).contains("/tmp/rec.mp4")
        assertThat(result.data).contains("\"max_sec\":10")
    }

    @Test
    fun `stop Stopped returns duration`() = runTest {
        holder.setRecorder(object : ScreenRecorder {
            override suspend fun start(request: RecordRequest) = StartResult.Started("/x")
            override suspend fun stop() = StopResult.Stopped("/x", 12345L)
            override fun isRecording() = false
            override fun isReady() = true
        })

        val result = executor.execute(
            ToolCall("4", "stop_screen_recording", emptyMap())
        )
        assertThat(result.success).isTrue()
        assertThat(result.data).contains("12345")
    }

    @Test
    fun `max_duration_sec clamps to 60`() = runTest {
        var captured: Int? = null
        holder.setRecorder(object : ScreenRecorder {
            override suspend fun start(request: RecordRequest): StartResult {
                captured = request.maxDurationSec
                return StartResult.Started("/x")
            }
            override suspend fun stop() = StopResult.NotRecording
            override fun isRecording() = false
            override fun isReady() = true
        })

        executor.execute(
            ToolCall("5", "start_screen_recording", mapOf("max_duration_sec" to 1000.0))
        )
        assertThat(captured).isEqualTo(60)
    }
}
