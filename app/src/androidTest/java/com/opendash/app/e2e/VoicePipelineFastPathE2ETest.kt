package com.opendash.app.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.opendash.app.e2e.fakes.FakeTextToSpeech
import com.opendash.app.tool.system.TimerManager
import com.opendash.app.voice.pipeline.VoicePipeline
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Full-pipeline E2E for the fast-path (priority-1) flow.
 *
 * Drives the **real** [VoicePipeline] from `processUserInput(text)`
 * onward — exactly as `AndroidSttProvider` would after a final STT
 * result — through:
 *
 *   FastPathRouter → ToolExecutor → TimerManager → TTS confirmation
 *
 * The only swap is the `TextToSpeech` binding, replaced with
 * [FakeTextToSpeech] via `FakeTtsTestModule.@TestInstallIn`. Everything
 * else (router, ToolExecutor, TimerManager singleton, FastPath matchers,
 * LatencyRecorder) is the production graph wired by Hilt.
 *
 * This guards the "Alexa feel" priority-1 contract that takes timer/light/
 * weather utterances from heard-text to spoken-confirmation without an
 * LLM round-trip — the regression that would silently break the headline
 * UX of the device.
 *
 * Notes:
 * - Uses real `Context` (DataStore, Room, AlarmManager). Test cleans
 *   timers in @After-equivalent logic at the end of each case.
 * - Bypasses STT/AudioRecord (no mic on emulators) and the wake word path.
 *   Wake→listening latency belongs in the L4 manual checklist.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class VoicePipelineFastPathE2ETest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var voicePipeline: VoicePipeline
    @Inject lateinit var fakeTts: FakeTextToSpeech
    @Inject lateinit var timerManager: TimerManager

    @Before
    fun setUp() {
        hiltRule.inject()
        fakeTts.reset()
    }

    @Test
    fun english_set_timer_speaks_confirmation_and_creates_timer() = runTest {
        // Snapshot any pre-existing timers so the assertion below is
        // strictly about the timer this test created.
        val before = timerManager.getActiveTimers().map { it.id }.toSet()

        try {
            withTimeout(PIPELINE_TIMEOUT_MS) {
                voicePipeline.processUserInput("set timer for 5 minutes")
            }

            // Fast-path produced spoken confirmation (and didn't fall
            // through to filler phrases, which would imply LLM path).
            val spoken = fakeTts.spokenTexts
            assertThat(spoken).isNotEmpty()
            // TimerMatcher's confirmation copy.
            assertThat(spoken.any { it.contains("Timer set", ignoreCase = true) })
                .isTrue()

            // Tool actually executed: a new active timer landed.
            val after = timerManager.getActiveTimers()
            val created = after.filter { it.id !in before }
            assertThat(created).hasSize(1)
            assertThat(created.first().totalSeconds).isEqualTo(5 * 60)
        } finally {
            // Don't leak alarms onto the device that runs the suite.
            cleanupNewTimers(before)
        }
    }

    @Test
    fun japanese_set_timer_speaks_confirmation_and_creates_timer() = runTest {
        val before = timerManager.getActiveTimers().map { it.id }.toSet()
        try {
            withTimeout(PIPELINE_TIMEOUT_MS) {
                voicePipeline.processUserInput("3分タイマー")
            }
            val spoken = fakeTts.spokenTexts
            assertThat(spoken).isNotEmpty()
            assertThat(spoken.any { it.contains("3分") }).isTrue()

            val after = timerManager.getActiveTimers()
            val created = after.filter { it.id !in before }
            assertThat(created).hasSize(1)
            assertThat(created.first().totalSeconds).isEqualTo(3 * 60)
        } finally {
            cleanupNewTimers(before)
        }
    }

    private suspend fun cleanupNewTimers(beforeIds: Set<String>) {
        timerManager.getActiveTimers()
            .filter { it.id !in beforeIds }
            .forEach { timerManager.cancelTimer(it.id) }
    }

    private companion object {
        // Generous: fast-path budget is 200 ms but cold-start of Hilt graph
        // and ToolExecutor on first invocation can spike on slow emulators.
        const val PIPELINE_TIMEOUT_MS = 5_000L
    }
}
