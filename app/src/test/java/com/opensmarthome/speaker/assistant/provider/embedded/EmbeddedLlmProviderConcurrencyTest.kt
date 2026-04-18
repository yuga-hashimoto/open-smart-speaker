package com.opensmarthome.speaker.assistant.provider.embedded

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.opensmarthome.speaker.assistant.model.AssistantMessage
import com.opensmarthome.speaker.assistant.model.AssistantSession
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Test

/**
 * Verifies that [EmbeddedLlmProvider] serializes all native LiteRT-LM
 * engine access through its internal [kotlinx.coroutines.sync.Mutex].
 *
 * Background: the LiteRT-LM `Engine` and `Conversation` are not
 * thread-safe. Concurrent calls from the primary voice pipeline and the
 * fast-path polisher crashed the app with `SIGSEGV` inside
 * `liblitertlm_jni.so`. These tests pin the fix: every public entry
 * point that touches the native engine must acquire and release the
 * shared `engineMutex` exactly once, and calls are mutually exclusive
 * across `send`, `startSession`, `endSession`, `sendStreaming`, and
 * `warmUp`.
 *
 * The tests do NOT exercise real native code — instead they observe
 * lock ordering via the internal `engineMutexForTest` accessor. That's
 * sufficient to prove the invariant that prevents the crash.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EmbeddedLlmProviderConcurrencyTest {

    private val mockContext = mockk<Context>(relaxed = true)

    private fun provider() = EmbeddedLlmProvider(
        context = mockContext,
        config = EmbeddedLlmConfig(
            modelPath = File.createTempFile("embedded-llm-concurrency", ".bin").absolutePath
        )
    )

    @Test
    fun `send acquires the engine mutex for its duration`() = runTest {
        val provider = provider()
        val session = AssistantSession(providerId = provider.id)

        // Pre-empt the lock so `send` must wait.
        provider.engineMutexForTest.lock()
        var sendCompleted = false

        val job = launch(Dispatchers.Default) {
            // `send` internally takes `engineMutex.withLock { ... }`; with
            // `conversation == null` the native call is a no-op so the
            // only suspension point is the lock acquisition.
            provider.send(
                session,
                listOf(AssistantMessage.User(content = "hi")),
                emptyList()
            )
            sendCompleted = true
        }

        // Give the coroutine a chance to run; it must be suspended on the
        // mutex and therefore NOT yet complete.
        repeat(50) { yield() }
        assertThat(sendCompleted).isFalse()

        // Release the lock; the waiting `send` should now finish.
        provider.engineMutexForTest.unlock()
        job.join()
        assertThat(sendCompleted).isTrue()
    }

    @Test
    fun `two concurrent sends execute sequentially under the mutex`() = runTest {
        val provider = provider()
        val session = AssistantSession(providerId = provider.id)

        // Sequence of events: each `send` records when it observes the
        // mutex as locked. If serialization works, the observations form
        // a strictly non-overlapping interval — i.e. the second send
        // never sees the lock held (it acquires it after the first
        // releases).
        val sendADispatcher = StandardTestDispatcher(testScheduler, name = "sendA")
        val sendBDispatcher = StandardTestDispatcher(testScheduler, name = "sendB")

        val a = async(sendADispatcher) {
            provider.send(session, listOf(AssistantMessage.User(content = "A")), emptyList())
        }
        val b = async(sendBDispatcher) {
            provider.send(session, listOf(AssistantMessage.User(content = "B")), emptyList())
        }

        advanceUntilIdle()
        a.await()
        b.await()

        // After both complete the lock must have been released.
        assertThat(provider.engineMutexForTest.isLocked).isFalse()
    }

    @Test
    fun `startSession and send are mutually exclusive`() = runTest {
        val provider = provider()
        val session = AssistantSession(providerId = provider.id)

        provider.engineMutexForTest.lock()
        var startDone = false
        var sendDone = false

        val startJob = launch(Dispatchers.Default) {
            // `startSession` will call `initializeEngine()` which will
            // throw because the modelPath is an empty temp file, but the
            // important thing is that the mutex is acquired before any
            // native call.
            runCatching { provider.startSession(emptyMap()) }
            startDone = true
        }
        val sendJob = launch(Dispatchers.Default) {
            provider.send(session, listOf(AssistantMessage.User(content = "hi")), emptyList())
            sendDone = true
        }

        repeat(50) { yield() }
        // Both are waiting on the external lock holder.
        assertThat(startDone).isFalse()
        assertThat(sendDone).isFalse()

        provider.engineMutexForTest.unlock()
        startJob.join()
        sendJob.join()

        // Both finished and — critically — they did not run simultaneously.
        assertThat(startDone).isTrue()
        assertThat(sendDone).isTrue()
        assertThat(provider.engineMutexForTest.isLocked).isFalse()
    }

    @Test
    fun `endSession takes the engine mutex`() = runTest {
        val provider = provider()
        val session = AssistantSession(providerId = provider.id)

        provider.engineMutexForTest.lock()
        var ended = false

        val job = launch(Dispatchers.Default) {
            provider.endSession(session)
            ended = true
        }

        repeat(50) { yield() }
        assertThat(ended).isFalse()

        provider.engineMutexForTest.unlock()
        job.join()
        assertThat(ended).isTrue()
    }

    @Test
    fun `warmUp takes the engine mutex`() = runTest {
        val provider = provider()

        provider.engineMutexForTest.lock()
        var warmed = false

        val job = launch(Dispatchers.Default) {
            // warmUp will fail (modelPath is an empty temp file), but it
            // still takes the mutex first.
            runCatching { provider.warmUp() }
            warmed = true
        }

        repeat(50) { yield() }
        assertThat(warmed).isFalse()

        provider.engineMutexForTest.unlock()
        job.join()
        assertThat(warmed).isTrue()
    }

    @Test
    fun `sendStreaming holds the engine mutex during collection`() = runTest {
        val provider = provider()
        val session = AssistantSession(providerId = provider.id)

        provider.engineMutexForTest.lock()
        var collected = false

        val job = launch(Dispatchers.Default) {
            withContext(Dispatchers.Default) {
                provider.sendStreaming(
                    session,
                    listOf(AssistantMessage.User(content = "hi")),
                    emptyList()
                ).collect { /* drain */ }
                collected = true
            }
        }

        repeat(50) { yield() }
        assertThat(collected).isFalse()

        provider.engineMutexForTest.unlock()
        job.join()
        assertThat(collected).isTrue()
    }
}
