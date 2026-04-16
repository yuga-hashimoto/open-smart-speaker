package com.opensmarthome.speaker.voice.fastpath

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class FastPathRouterTest {

    private val router = DefaultFastPathRouter()

    @Test
    fun `set timer for 5 minutes`() {
        val m = router.match("Set timer for 5 minutes")
        assertThat(m).isNotNull()
        assertThat(m!!.toolName).isEqualTo("set_timer")
        assertThat(m.arguments["seconds"]).isEqualTo(300.0)
    }

    @Test
    fun `timer 10 seconds`() {
        val m = router.match("timer 10 seconds")
        assertThat(m?.arguments?.get("seconds")).isEqualTo(10.0)
    }

    @Test
    fun `japanese 5-minute timer`() {
        val m = router.match("5分タイマー")
        assertThat(m?.toolName).isEqualTo("set_timer")
        assertThat(m?.arguments?.get("seconds")).isEqualTo(300.0)
    }

    @Test
    fun `japanese timer with seconds`() {
        val m = router.match("10秒タイマー")
        assertThat(m?.arguments?.get("seconds")).isEqualTo(10.0)
    }

    @Test
    fun `what time is it`() {
        val m = router.match("What time is it?")
        assertThat(m?.toolName).isEqualTo("get_datetime")
    }

    @Test
    fun `japanese time query`() {
        val m = router.match("今何時?")
        assertThat(m?.toolName).isEqualTo("get_datetime")
    }

    @Test
    fun `volume up`() {
        val m = router.match("Volume up")
        assertThat(m?.toolName).isEqualTo("set_volume")
        assertThat(m?.arguments?.get("level")).isEqualTo(70.0)
    }

    @Test
    fun `louder`() {
        val m = router.match("Louder please")
        assertThat(m?.toolName).isEqualTo("set_volume")
    }

    @Test
    fun `volume down japanese`() {
        val m = router.match("音量を下げて")
        assertThat(m?.toolName).isEqualTo("set_volume")
        assertThat(m?.arguments?.get("level")).isEqualTo(30.0)
    }

    @Test
    fun `set volume to 50`() {
        val m = router.match("set volume to 50")
        assertThat(m?.toolName).isEqualTo("set_volume")
        assertThat(m?.arguments?.get("level")).isEqualTo(50.0)
    }

    @Test
    fun `set volume clamps over 100`() {
        val m = router.match("set volume to 200")
        assertThat(m?.arguments?.get("level")).isEqualTo(100.0)
    }

    @Test
    fun `lights on`() {
        val m = router.match("Turn the lights on")
        assertThat(m?.toolName).isEqualTo("execute_command")
        assertThat(m?.arguments?.get("action")).isEqualTo("turn_on")
    }

    @Test
    fun `lights off japanese`() {
        val m = router.match("電気を消して")
        assertThat(m?.toolName).isEqualTo("execute_command")
        assertThat(m?.arguments?.get("action")).isEqualTo("turn_off")
    }

    @Test
    fun `date query`() {
        val m = router.match("What's today's date?")
        assertThat(m?.toolName).isEqualTo("get_datetime")
    }

    @Test
    fun `unknown utterance returns null`() {
        val m = router.match("Tell me a long story about pirates in 17th century Caribbean")
        assertThat(m).isNull()
    }

    @Test
    fun `empty input returns null`() {
        assertThat(router.match("")).isNull()
        assertThat(router.match("   ")).isNull()
    }

    @Test
    fun `japanese lights on`() {
        val m = router.match("電気をつけて")
        assertThat(m?.toolName).isEqualTo("execute_command")
        assertThat(m?.arguments?.get("action")).isEqualTo("turn_on")
    }

    @Test
    fun `cancel all timers fast-path`() {
        val m = router.match("cancel all timers")
        assertThat(m?.toolName).isEqualTo("cancel_all_timers")
    }

    @Test
    fun `stop timers also matches`() {
        val m = router.match("stop timers")
        assertThat(m?.toolName).isEqualTo("cancel_all_timers")
    }

    @Test
    fun `japanese cancel all timers`() {
        val m = router.match("タイマーを全部止めて")
        assertThat(m?.toolName).isEqualTo("cancel_all_timers")
    }

    @Test
    fun `pause music fast-path`() {
        val m = router.match("pause music")
        assertThat(m?.toolName).isEqualTo("execute_command")
        assertThat(m?.arguments?.get("action")).isEqualTo("media_pause")
        assertThat(m?.arguments?.get("device_type")).isEqualTo("media_player")
    }

    @Test
    fun `next track fast-path`() {
        val m = router.match("next track")
        assertThat(m?.arguments?.get("action")).isEqualTo("media_next_track")
    }

    @Test
    fun `japanese pause fast-path`() {
        val m = router.match("音楽を止めて")
        assertThat(m?.arguments?.get("action")).isEqualTo("media_pause")
    }

    @Test
    fun `japanese next song`() {
        val m = router.match("次の曲")
        assertThat(m?.arguments?.get("action")).isEqualTo("media_next_track")
    }

    @Test
    fun `thanks gets speak-only reply`() {
        val m = router.match("thanks")
        assertThat(m?.toolName).isNull()
        assertThat(m?.spokenConfirmation?.lowercase()).contains("welcome")
    }

    @Test
    fun `hello gets greeting reply`() {
        val m = router.match("hello")
        assertThat(m?.toolName).isNull()
        assertThat(m?.spokenConfirmation).isNotNull()
    }

    @Test
    fun `japanese arigatou triggers welcome`() {
        val m = router.match("ありがとう")
        assertThat(m?.toolName).isNull()
        assertThat(m?.spokenConfirmation).contains("どういたしまして")
    }

    @Test
    fun `help utterance returns speak-only match`() {
        val m = router.match("help")
        assertThat(m).isNotNull()
        assertThat(m!!.toolName).isNull()
        assertThat(m.spokenConfirmation).isNotNull()
        assertThat(m.spokenConfirmation!!.lowercase()).contains("timer")
    }

    @Test
    fun `what can you do returns speak-only help`() {
        val m = router.match("What can you do?")
        assertThat(m?.toolName).isNull()
        assertThat(m?.spokenConfirmation).isNotNull()
    }

    @Test
    fun `japanese help`() {
        val m = router.match("できることを教えて")
        assertThat(m?.toolName).isNull()
        assertThat(m?.spokenConfirmation).isNotNull()
    }
}
