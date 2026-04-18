package com.opendash.app.voice.fastpath

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LockScreenMatcherTest {

    private fun match(s: String): FastPathMatch? = LockScreenMatcher.tryMatch(s.lowercase().trim())

    @Test
    fun `english lock the screen matches`() {
        assertThat(match("lock the screen")?.toolName).isEqualTo("lock_screen")
    }

    @Test
    fun `english lock screen without article matches`() {
        assertThat(match("lock screen")?.toolName).isEqualTo("lock_screen")
    }

    @Test
    fun `english lock the tablet matches`() {
        assertThat(match("lock the tablet")?.toolName).isEqualTo("lock_screen")
    }

    @Test
    fun `english screen off matches`() {
        assertThat(match("screen off")?.toolName).isEqualTo("lock_screen")
    }

    @Test
    fun `japanese 画面をロック matches`() {
        assertThat(match("画面をロックして")?.toolName).isEqualTo("lock_screen")
    }

    @Test
    fun `japanese スクリーンロック matches`() {
        assertThat(match("スクリーンロックして")?.toolName).isEqualTo("lock_screen")
    }

    @Test
    fun `japanese 画面を消して matches`() {
        assertThat(match("画面を消して")?.toolName).isEqualTo("lock_screen")
    }

    @Test
    fun `unrelated utterances do not match`() {
        assertThat(match("what time is it")).isNull()
        assertThat(match("set a timer for five minutes")).isNull()
        assertThat(match("turn off the lights")).isNull()
        assertThat(match("lock the door")).isNull() // "door" not screen/tablet/etc
    }

    @Test
    fun `match is tool-dispatch not speak-only`() {
        val result = match("lock the screen")
        assertThat(result?.toolName).isEqualTo("lock_screen")
        assertThat(result?.spokenConfirmation).isNull()
    }
}
