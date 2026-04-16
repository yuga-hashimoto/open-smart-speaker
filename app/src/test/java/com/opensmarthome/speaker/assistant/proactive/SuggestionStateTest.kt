package com.opensmarthome.speaker.assistant.proactive

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SuggestionStateTest {

    @Test
    fun `dismiss removes suggestion and remembers it`() = runTest {
        val engine = SuggestionEngine(
            rules = listOf(
                object : SuggestionRule {
                    override suspend fun evaluate(context: ProactiveContext) =
                        Suggestion("s1", Suggestion.Priority.LOW, "msg")
                }
            )
        )
        val dispatcher = StandardTestDispatcher(testScheduler)
        val state = SuggestionState(engine, pollIntervalMs = 100L, scope = CoroutineScope(dispatcher))

        state.start()
        advanceTimeBy(50)
        runCurrent()
        assertThat(state.current.value.map { it.id }).contains("s1")

        state.dismiss("s1")
        assertThat(state.current.value).isEmpty()
        assertThat(state.dismissed.value).contains("s1")

        // Next poll cycle should still filter it out
        advanceTimeBy(200)
        runCurrent()
        assertThat(state.current.value).isEmpty()

        state.stop()
    }

    @Test
    fun `clearDismissals allows suggestion to return`() = runTest {
        val engine = SuggestionEngine(
            rules = listOf(
                object : SuggestionRule {
                    override suspend fun evaluate(context: ProactiveContext) =
                        Suggestion("s1", Suggestion.Priority.LOW, "msg")
                }
            )
        )
        val dispatcher = StandardTestDispatcher(testScheduler)
        val state = SuggestionState(engine, pollIntervalMs = 100L, scope = CoroutineScope(dispatcher))

        state.start()
        advanceTimeBy(50)
        runCurrent()

        state.dismiss("s1")
        state.clearDismissals()

        advanceTimeBy(150)
        runCurrent()
        assertThat(state.current.value.map { it.id }).contains("s1")

        state.stop()
    }

    @Test
    fun `failing rule does not crash state`() = runTest {
        val engine = SuggestionEngine(
            rules = listOf(
                object : SuggestionRule {
                    override suspend fun evaluate(context: ProactiveContext): Suggestion =
                        throw RuntimeException("boom")
                }
            )
        )
        val dispatcher = StandardTestDispatcher(testScheduler)
        val state = SuggestionState(engine, pollIntervalMs = 100L, scope = CoroutineScope(dispatcher))

        state.start()
        advanceTimeBy(150)
        runCurrent()

        // Should just be empty, not throw
        assertThat(state.current.value).isEmpty()
        state.stop()
    }
}
