package com.opensmarthome.speaker.voice.fastpath

import com.google.common.truth.Truth.assertThat
import com.opensmarthome.speaker.tool.system.TimerInfo
import com.opensmarthome.speaker.tool.system.TimerManager
import org.junit.jupiter.api.Test

/**
 * Matcher test uses a hand-written fake [TimerManager] because the real
 * implementation depends on Android AlarmManager. MockK spyk on fun
 * interface / suspend function has caused NoClassDefFoundError on JVM 21
 * previously; capturing lambdas / fakes are safer.
 */
class CancelTimerByLabelMatcherTest {

    private class FakeTimerManager(private val timers: List<TimerInfo>) : TimerManager {
        override suspend fun setTimer(seconds: Int, label: String): String = "unused"
        override suspend fun cancelTimer(timerId: String): Boolean = true
        override suspend fun getActiveTimers(): List<TimerInfo> = timers
    }

    private fun matcher(vararg timers: TimerInfo): CancelTimerByLabelMatcher =
        CancelTimerByLabelMatcher(FakeTimerManager(timers.toList()))

    private fun timer(id: String, label: String): TimerInfo =
        TimerInfo(id = id, label = label, remainingSeconds = 120, totalSeconds = 300)

    @Test
    fun `english cancel the pasta timer resolves to pasta id`() {
        val m = matcher(timer("t1", "pasta"), timer("t2", "chicken"))
        val result = m.tryMatch("cancel the pasta timer")
        assertThat(result?.toolName).isEqualTo("cancel_timer")
        assertThat(result?.arguments).isEqualTo(mapOf<String, Any?>("timer_id" to "t1"))
        assertThat(result?.spokenConfirmation).isEqualTo("Cancelled the pasta timer.")
    }

    @Test
    fun `english stop the chicken timer resolves to chicken id`() {
        val m = matcher(timer("t1", "pasta"), timer("t2", "chicken"))
        val result = m.tryMatch("stop the chicken timer")
        assertThat(result?.arguments?.get("timer_id")).isEqualTo("t2")
    }

    @Test
    fun `japanese パスタのタイマーを止めて resolves to pasta id`() {
        val m = matcher(timer("t1", "パスタ"), timer("t2", "チキン"))
        val result = m.tryMatch("パスタのタイマーを止めて")
        assertThat(result?.toolName).isEqualTo("cancel_timer")
        assertThat(result?.arguments).isEqualTo(mapOf<String, Any?>("timer_id" to "t1"))
    }

    @Test
    fun `japanese キャンセル verb resolves correctly`() {
        val m = matcher(timer("t1", "パスタ"), timer("t2", "チキン"))
        val result = m.tryMatch("チキンのタイマーをキャンセル")
        assertThat(result?.arguments?.get("timer_id")).isEqualTo("t2")
    }

    @Test
    fun `label not in list returns null to fall through to LLM`() {
        val m = matcher(timer("t1", "pasta"), timer("t2", "chicken"))
        val result = m.tryMatch("cancel the salad timer")
        assertThat(result).isNull()
    }

    @Test
    fun `empty timer list returns null`() {
        val m = matcher()
        val result = m.tryMatch("cancel the pasta timer")
        assertThat(result).isNull()
    }

    @Test
    fun `cancel all timers returns null so CancelAllTimersMatcher owns it`() {
        val m = matcher(timer("t1", "pasta"))
        assertThat(m.tryMatch("cancel all timers")).isNull()
        assertThat(m.tryMatch("stop every timer")).isNull()
        assertThat(m.tryMatch("タイマー全部止めて")).isNull()
        assertThat(m.tryMatch("タイマー全て止めて")).isNull()
    }

    @Test
    fun `case insensitive label match`() {
        val m = matcher(timer("t1", "Pasta"))
        val result = m.tryMatch("cancel the pasta timer")
        assertThat(result?.arguments?.get("timer_id")).isEqualTo("t1")
    }

    @Test
    fun `partial contains matches — labelled 'pasta sauce' still matched by 'pasta'`() {
        val m = matcher(timer("t1", "pasta sauce"), timer("t2", "chicken"))
        val result = m.tryMatch("cancel the pasta timer")
        assertThat(result?.arguments?.get("timer_id")).isEqualTo("t1")
    }

    @Test
    fun `unrelated utterance returns null`() {
        val m = matcher(timer("t1", "pasta"))
        assertThat(m.tryMatch("what's the weather")).isNull()
        assertThat(m.tryMatch("set a timer for 5 minutes")).isNull()
    }
}
