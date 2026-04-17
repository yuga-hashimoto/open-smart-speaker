package com.opensmarthome.speaker.multiroom

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MultiroomLifecycleControllerTest {

    private class Recorder {
        var startCount = 0
        var stopCount = 0
        var lastOp: String? = null
        val ops = mutableListOf<String>()

        suspend fun onStart() {
            startCount++
            lastOp = "start"
            ops += "start"
        }

        fun onStop() {
            stopCount++
            lastOp = "stop"
            ops += "stop"
        }
    }

    private fun controller(recorder: Recorder) = MultiroomLifecycleController(
        onStart = { recorder.onStart() },
        onStop = { recorder.onStop() },
    )

    @Test
    fun `setEnabled true starts multiroom`() = runTest {
        val r = Recorder()
        val ctrl = controller(r)
        ctrl.setEnabled(true)
        assertThat(r.startCount).isEqualTo(1)
        assertThat(r.stopCount).isEqualTo(0)
        assertThat(ctrl.isActive).isTrue()
    }

    @Test
    fun `setEnabled true twice is idempotent`() = runTest {
        val r = Recorder()
        val ctrl = controller(r)
        ctrl.setEnabled(true)
        ctrl.setEnabled(true)
        assertThat(r.startCount).isEqualTo(1)
        assertThat(r.stopCount).isEqualTo(0)
    }

    @Test
    fun `setEnabled false when inactive is no-op`() = runTest {
        val r = Recorder()
        val ctrl = controller(r)
        ctrl.setEnabled(false)
        assertThat(r.startCount).isEqualTo(0)
        assertThat(r.stopCount).isEqualTo(0)
        assertThat(ctrl.isActive).isFalse()
    }

    @Test
    fun `setEnabled false after true stops multiroom`() = runTest {
        val r = Recorder()
        val ctrl = controller(r)
        ctrl.setEnabled(true)
        ctrl.setEnabled(false)
        assertThat(r.startCount).isEqualTo(1)
        assertThat(r.stopCount).isEqualTo(1)
        assertThat(ctrl.isActive).isFalse()
        assertThat(r.ops).containsExactly("start", "stop").inOrder()
    }

    @Test
    fun `rapid toggle sequence produces matching start-stop pairs`() = runTest {
        val r = Recorder()
        val ctrl = controller(r)
        ctrl.setEnabled(true)
        ctrl.setEnabled(false)
        ctrl.setEnabled(true)
        ctrl.setEnabled(false)
        assertThat(r.startCount).isEqualTo(2)
        assertThat(r.stopCount).isEqualTo(2)
        assertThat(r.ops).containsExactly("start", "stop", "start", "stop").inOrder()
    }

    @Test
    fun `onStart failure does not leave controller stuck active`() = runTest {
        var attempts = 0
        val ctrl = MultiroomLifecycleController(
            onStart = {
                attempts++
                error("boom")
            },
            onStop = {},
        )
        runCatching { ctrl.setEnabled(true) }
        assertThat(attempts).isEqualTo(1)
        assertThat(ctrl.isActive).isFalse()
        // Retry should be allowed because the previous attempt never marked us active.
        runCatching { ctrl.setEnabled(true) }
        assertThat(attempts).isEqualTo(2)
    }

    @Test
    fun `onStop exception is swallowed and still transitions to inactive`() = runTest {
        var stopCalls = 0
        val ctrl = MultiroomLifecycleController(
            onStart = {},
            onStop = {
                stopCalls++
                error("drain failed")
            },
        )
        ctrl.setEnabled(true)
        ctrl.setEnabled(false)
        assertThat(stopCalls).isEqualTo(1)
        assertThat(ctrl.isActive).isFalse()
    }
}
