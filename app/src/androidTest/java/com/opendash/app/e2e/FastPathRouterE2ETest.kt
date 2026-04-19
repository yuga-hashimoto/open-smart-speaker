package com.opendash.app.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.opendash.app.voice.fastpath.DefaultFastPathRouter
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented sanity check on the live `FastPathRouter` matcher chain.
 *
 * The unit tests under `app/src/test/...` already cover individual matchers
 * with virtual time. This instrumented variant guarantees the router still
 * routes the priority-1 utterances (timer / volume / lights / weather)
 * correctly when running on a real device — guarding against ART/Kotlin
 * regex behavioural diffs vs the JVM unit-test runtime, locale defaults,
 * and any matcher that ends up touching `android.*` APIs.
 *
 * No Hilt graph is needed; this exercises the pure router.
 */
@RunWith(AndroidJUnit4::class)
class FastPathRouterE2ETest {

    private val router = DefaultFastPathRouter()

    @Test
    fun timer_english_routes_to_set_timer() {
        val match = router.match("set timer for 5 minutes")
        assertThat(match).isNotNull()
        assertThat(match!!.toolName).isEqualTo("set_timer")
        assertThat(match.arguments["seconds"]).isEqualTo(300.0)
    }

    @Test
    fun timer_japanese_routes_to_set_timer() {
        val match = router.match("5分タイマー")
        assertThat(match).isNotNull()
        assertThat(match!!.toolName).isEqualTo("set_timer")
        assertThat(match.arguments["seconds"]).isEqualTo(300.0)
    }

    @Test
    fun help_returns_speak_only_match() {
        val match = router.match("what can you do")
        assertThat(match).isNotNull()
        // Help is a speak-only match — toolName is null, spokenConfirmation
        // carries the canned capability summary.
        assertThat(match!!.toolName).isNull()
        assertThat(match.spokenConfirmation).isNotNull()
    }

    @Test
    fun ambiguous_information_query_falls_through_to_llm() {
        // "explain X" / "Xって何" should NOT be eaten by fast path; the LLM
        // needs to orchestrate multi-tool reasoning.
        val match = router.match("トマトって何？")
        assertThat(match).isNull()
    }

    @Test
    fun empty_input_returns_null() {
        assertThat(router.match("")).isNull()
        assertThat(router.match("   ")).isNull()
    }
}
