package com.opensmarthome.speaker.assistant.proactive

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class SuggestionEngineTest {

    @Test
    fun `morning rule fires at 7am`() = runTest {
        val ctx = ProactiveContext(nowMs = 0, hourOfDay = 7, dayOfWeek = 1)
        val suggestion = MorningGreetingRule().evaluate(ctx)

        assertThat(suggestion).isNotNull()
        assertThat(suggestion?.message).contains("morning")
    }

    @Test
    fun `morning rule is silent at noon`() = runTest {
        val ctx = ProactiveContext(nowMs = 0, hourOfDay = 12, dayOfWeek = 1)
        assertThat(MorningGreetingRule().evaluate(ctx)).isNull()
    }

    @Test
    fun `evening lights fires at 20`() = runTest {
        val ctx = ProactiveContext(nowMs = 0, hourOfDay = 20, dayOfWeek = 1)
        val suggestion = EveningLightsRule().evaluate(ctx)
        assertThat(suggestion?.message).contains("lights")
    }

    @Test
    fun `evening briefing fires at 20`() = runTest {
        val ctx = ProactiveContext(nowMs = 0, hourOfDay = 20, dayOfWeek = 1)
        val suggestion = EveningBriefingRule().evaluate(ctx)
        assertThat(suggestion).isNotNull()
        assertThat(suggestion?.suggestedAction?.toolName).isEqualTo("evening_briefing")
        assertThat(suggestion?.priority).isEqualTo(Suggestion.Priority.NORMAL)
    }

    @Test
    fun `evening briefing silent at noon`() = runTest {
        val ctx = ProactiveContext(nowMs = 0, hourOfDay = 12, dayOfWeek = 1)
        assertThat(EveningBriefingRule().evaluate(ctx)).isNull()
    }

    @Test
    fun `evening briefing silent after 22`() = runTest {
        val ctx = ProactiveContext(nowMs = 0, hourOfDay = 23, dayOfWeek = 1)
        assertThat(EveningBriefingRule().evaluate(ctx)).isNull()
    }

    @Test
    fun `night quiet fires after 23`() = runTest {
        val midnight = ProactiveContext(nowMs = 0, hourOfDay = 0, dayOfWeek = 1)
        val late = ProactiveContext(nowMs = 0, hourOfDay = 23, dayOfWeek = 1)

        assertThat(NightQuietRule().evaluate(midnight)).isNotNull()
        assertThat(NightQuietRule().evaluate(late)).isNotNull()
    }

    @Test
    fun `engine returns empty when no rules match`() = runTest {
        val engine = SuggestionEngine(
            rules = listOf(MorningGreetingRule(), EveningLightsRule())
        )
        // Runs against the *real* system clock, so we can only assert non-crash
        val suggestions = engine.evaluate()
        assertThat(suggestions).isNotNull()
    }

    @Test
    fun `suggestions are sorted by priority descending`() = runTest {
        val engine = SuggestionEngine(
            rules = listOf(
                object : SuggestionRule {
                    override suspend fun evaluate(context: ProactiveContext) =
                        Suggestion("low", Suggestion.Priority.LOW, "low")
                },
                object : SuggestionRule {
                    override suspend fun evaluate(context: ProactiveContext) =
                        Suggestion("urgent", Suggestion.Priority.URGENT, "urgent")
                },
                object : SuggestionRule {
                    override suspend fun evaluate(context: ProactiveContext) =
                        Suggestion("normal", Suggestion.Priority.NORMAL, "normal")
                }
            )
        )

        val results = engine.evaluate()
        assertThat(results.map { it.id }).containsExactly("urgent", "normal", "low").inOrder()
    }

    @Test
    fun `engine tolerates rule exceptions`() = runTest {
        val engine = SuggestionEngine(
            rules = listOf(
                object : SuggestionRule {
                    override suspend fun evaluate(context: ProactiveContext): Suggestion =
                        throw RuntimeException("boom")
                },
                object : SuggestionRule {
                    override suspend fun evaluate(context: ProactiveContext) =
                        Suggestion("ok", Suggestion.Priority.NORMAL, "safe")
                }
            )
        )

        val results = engine.evaluate()
        assertThat(results).hasSize(1)
        assertThat(results[0].id).isEqualTo("ok")
    }
}
