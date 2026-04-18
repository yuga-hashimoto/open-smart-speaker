package com.opendash.app.voice.fastpath

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Sanity-check that the fast path is actually fast: with all default
 * matchers a typical query must complete in microseconds. We don't want
 * the matcher list to balloon to hundreds of regex evaluations per turn.
 *
 * Loose assertion (1ms per call) so the test isn't flaky on slow CI hardware.
 */
class FastPathRouterPerfTest {

    private val router = DefaultFastPathRouter()

    @Test
    fun `match completes in under 1ms even on miss`() {
        // Warm up regex caches
        repeat(50) { router.match("hello there from somewhere") }

        val iterations = 1_000
        val totalNs = (1..iterations).sumOf {
            val start = System.nanoTime()
            router.match("set timer for 5 minutes")
            System.nanoTime() - start
        }
        val avgUs = totalNs / iterations / 1_000.0
        assertThat(avgUs).isLessThan(1_000.0) // <1ms per call
    }

    @Test
    fun `match returns null for non-matching long input`() {
        val long = "a".repeat(500) + " jumbled words that don't match anything"
        val match = router.match(long)
        assertThat(match).isNull()
    }
}
