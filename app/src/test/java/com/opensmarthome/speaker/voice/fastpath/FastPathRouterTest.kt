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
    fun `mute sets volume to zero`() {
        val m = router.match("mute")
        assertThat(m?.toolName).isEqualTo("set_volume")
        assertThat(m?.arguments?.get("level")).isEqualTo(0.0)
    }

    @Test
    fun `be quiet mutes`() {
        val m = router.match("be quiet")
        assertThat(m?.arguments?.get("level")).isEqualTo(0.0)
    }

    @Test
    fun `unmute restores moderate level`() {
        val m = router.match("unmute")
        assertThat(m?.toolName).isEqualTo("set_volume")
        assertThat(m?.arguments?.get("level")).isEqualTo(50.0)
    }

    @Test
    fun `japanese mute`() {
        val m = router.match("ミュート")
        assertThat(m?.arguments?.get("level")).isEqualTo(0.0)
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
    fun `list timers fast-path`() {
        val m = router.match("list timers")
        assertThat(m?.toolName).isEqualTo("get_timers")
    }

    @Test
    fun `what timers do I have fast-path`() {
        val m = router.match("what timers do I have")
        assertThat(m?.toolName).isEqualTo("get_timers")
    }

    @Test
    fun `show my timers fast-path`() {
        val m = router.match("show my timers")
        assertThat(m?.toolName).isEqualTo("get_timers")
    }

    @Test
    fun `japanese list timers`() {
        val m = router.match("タイマー一覧")
        assertThat(m?.toolName).isEqualTo("get_timers")
    }

    @Test
    fun `cancel all still wins over list timers`() {
        // Precedence guard: 'cancel all timers' must trigger cancel, not list.
        val m = router.match("cancel all timers")
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
    fun `thank you so much gets speak-only reply`() {
        val m = router.match("thank you so much")
        assertThat(m?.toolName).isNull()
        assertThat(m?.spokenConfirmation?.lowercase()).contains("welcome")
    }

    @Test
    fun `thanks a lot gets speak-only reply`() {
        val m = router.match("thanks a lot")
        assertThat(m?.toolName).isNull()
        assertThat(m?.spokenConfirmation?.lowercase()).contains("welcome")
    }

    @Test
    fun `appreciate it gets speak-only reply`() {
        val m = router.match("appreciate it")
        assertThat(m?.toolName).isNull()
        assertThat(m?.spokenConfirmation?.lowercase()).contains("welcome")
    }

    @Test
    fun `many thanks gets speak-only reply`() {
        val m = router.match("many thanks")
        assertThat(m?.toolName).isNull()
        assertThat(m?.spokenConfirmation?.lowercase()).contains("welcome")
    }

    @Test
    fun `japanese kansha triggers welcome`() {
        val m = router.match("感謝します")
        assertThat(m?.toolName).isNull()
        assertThat(m?.spokenConfirmation).contains("どういたしまして")
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
    fun `english help mentions agent capabilities`() {
        val m = router.match("what can you do")
        val text = m?.spokenConfirmation?.lowercase() ?: ""
        // Surface OpenClaw-class capabilities, not just smart-speaker basics.
        assertThat(text).contains("weather")
        assertThat(text).contains("news")
        assertThat(text).contains("skill")
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

    @Test
    fun `japanese help mentions agent capabilities`() {
        val m = router.match("できることを教えて")
        val text = m?.spokenConfirmation ?: ""
        assertThat(text).contains("天気")
        assertThat(text).contains("ニュース")
        assertThat(text).contains("スキル")
    }

    @Test
    fun `open camera launches app`() {
        val m = router.match("open camera")
        assertThat(m?.toolName).isEqualTo("launch_app")
        assertThat(m?.arguments?.get("app_name")).isEqualTo("camera")
    }

    @Test
    fun `launch maps launches app`() {
        val m = router.match("launch maps")
        assertThat(m?.toolName).isEqualTo("launch_app")
        assertThat(m?.arguments?.get("app_name")).isEqualTo("maps")
    }

    @Test
    fun `open the calculator strips article`() {
        val m = router.match("open the calculator")
        assertThat(m?.arguments?.get("app_name")).isEqualTo("calculator")
    }

    @Test
    fun `open lights does not launch an app`() {
        // LightsMatcher should handle this, not LaunchAppMatcher
        val m = router.match("turn the lights on")
        assertThat(m?.toolName).isNotEqualTo("launch_app")
    }

    @Test
    fun `open the door is not launched as an app`() {
        // Smart-home controllable — must fall through to the LLM, not become launch_app("door").
        val m = router.match("open the door")
        assertThat(m?.toolName).isNotEqualTo("launch_app")
    }

    @Test
    fun `open the garage is not launched as an app`() {
        val m = router.match("open the garage")
        assertThat(m?.toolName).isNotEqualTo("launch_app")
    }

    @Test
    fun `open the blinds is not launched as an app`() {
        val m = router.match("open the blinds")
        assertThat(m?.toolName).isNotEqualTo("launch_app")
    }

    @Test
    fun `lock the door is not launched as an app`() {
        // 'lock' alias also reserved.
        val m = router.match("open the lock")
        assertThat(m?.toolName).isNotEqualTo("launch_app")
    }

    @Test
    fun `japanese open door is not launched as an app`() {
        val m = router.match("ドアを開いて")
        assertThat(m?.toolName).isNotEqualTo("launch_app")
    }

    @Test
    fun `japanese open curtains is not launched as an app`() {
        val m = router.match("カーテンを開いて")
        assertThat(m?.toolName).isNotEqualTo("launch_app")
    }

    @Test
    fun `turn off everything`() {
        val m = router.match("turn off everything")
        assertThat(m?.toolName).isEqualTo("execute_command")
        assertThat(m?.arguments?.get("action")).isEqualTo("turn_off")
    }

    @Test
    fun `japanese everything off`() {
        val m = router.match("全部消して")
        assertThat(m?.arguments?.get("action")).isEqualTo("turn_off")
    }

    @Test
    fun `turn it off does not match everything-off`() {
        val m = router.match("turn it off")
        assertThat(m?.spokenConfirmation).isNotEqualTo("Everything off.")
    }

    @Test
    fun `find my tablet rings device`() {
        val m = router.match("find my tablet")
        assertThat(m?.toolName).isEqualTo("find_device")
    }

    @Test
    fun `japanese find device`() {
        val m = router.match("デバイスを探して")
        assertThat(m?.toolName).isEqualTo("find_device")
    }

    @Test
    fun `news fast-path`() {
        val m = router.match("news")
        assertThat(m?.toolName).isEqualTo("get_news")
    }

    @Test
    fun `whats on my calendar today fast-path`() {
        val m = router.match("what's on my calendar today")
        assertThat(m?.toolName).isEqualTo("get_calendar_events")
        assertThat(m?.arguments?.get("days_ahead")).isEqualTo(1.0)
    }

    @Test
    fun `do I have any meetings today fast-path`() {
        val m = router.match("do i have any meetings today")
        assertThat(m?.toolName).isEqualTo("get_calendar_events")
    }

    @Test
    fun `whats my schedule fast-path`() {
        val m = router.match("what's my schedule")
        assertThat(m?.toolName).isEqualTo("get_calendar_events")
    }

    @Test
    fun `japanese today schedule fast-path`() {
        val m = router.match("今日の予定")
        assertThat(m?.toolName).isEqualTo("get_calendar_events")
    }

    @Test
    fun `japanese today meeting fast-path`() {
        val m = router.match("今日のミーティング")
        assertThat(m?.toolName).isEqualTo("get_calendar_events")
    }

    @Test
    fun `what do you remember calls list_memory`() {
        val m = router.match("what do you remember")
        assertThat(m?.toolName).isEqualTo("list_memory")
    }

    @Test
    fun `japanese list memory`() {
        val m = router.match("覚えていること")
        assertThat(m?.toolName).isEqualTo("list_memory")
    }

    @Test
    fun `tell me the news`() {
        val m = router.match("tell me the news")
        assertThat(m?.toolName).isEqualTo("get_news")
    }

    @Test
    fun `japanese news`() {
        val m = router.match("ニュース")
        assertThat(m?.toolName).isEqualTo("get_news")
    }

    @Test
    fun `whats the weather`() {
        val m = router.match("what's the weather")
        assertThat(m?.toolName).isEqualTo("get_weather")
    }

    @Test
    fun `japanese weather today`() {
        val m = router.match("今日の天気")
        assertThat(m?.toolName).isEqualTo("get_weather")
    }

    @Test
    fun `set thermostat to 22 dispatches climate set_temperature`() {
        val m = router.match("set thermostat to 22")
        assertThat(m?.toolName).isEqualTo("execute_command")
        assertThat(m?.arguments?.get("device_type")).isEqualTo("climate")
        assertThat(m?.arguments?.get("action")).isEqualTo("set_temperature")
        @Suppress("UNCHECKED_CAST")
        val params = m?.arguments?.get("parameters") as? Map<String, Any?>
        assertThat(params?.get("temperature")).isEqualTo(22)
    }

    @Test
    fun `change AC to 26 degrees`() {
        val m = router.match("change ac to 26 degrees")
        assertThat(m?.arguments?.get("action")).isEqualTo("set_temperature")
    }

    @Test
    fun `japanese aircon temperature`() {
        val m = router.match("エアコン25度")
        assertThat(m?.arguments?.get("action")).isEqualTo("set_temperature")
        @Suppress("UNCHECKED_CAST")
        val params = m?.arguments?.get("parameters") as? Map<String, Any?>
        assertThat(params?.get("temperature")).isEqualTo(25)
    }

    @Test
    fun `thermostat clamps to safe range`() {
        val m = router.match("set thermostat to 5")
        @Suppress("UNCHECKED_CAST")
        val params = m?.arguments?.get("parameters") as? Map<String, Any?>
        assertThat(params?.get("temperature")).isEqualTo(10)
    }

    @Test
    fun `run goodnight routine`() {
        val m = router.match("run goodnight routine")
        assertThat(m?.toolName).isEqualTo("run_routine")
        assertThat(m?.arguments?.get("name")).isEqualTo("goodnight")
    }

    @Test
    fun `execute morning routine`() {
        val m = router.match("execute morning routine")
        assertThat(m?.arguments?.get("name")).isEqualTo("morning")
    }

    @Test
    fun `japanese run routine`() {
        val m = router.match("おやすみルーチンを実行")
        assertThat(m?.toolName).isEqualTo("run_routine")
        assertThat(m?.arguments?.get("name")).isEqualTo("おやすみ")
    }

    @Test
    fun `bedroom lights off scopes to room`() {
        val m = router.match("turn off the bedroom lights")
        assertThat(m?.toolName).isEqualTo("execute_command")
        assertThat(m?.arguments?.get("room")).isEqualTo("bedroom")
        assertThat(m?.arguments?.get("action")).isEqualTo("turn_off")
    }

    @Test
    fun `kitchen lights on scopes to room`() {
        val m = router.match("kitchen lights on")
        assertThat(m?.arguments?.get("room")).isEqualTo("kitchen")
        assertThat(m?.arguments?.get("action")).isEqualTo("turn_on")
    }

    @Test
    fun `japanese room lights on`() {
        val m = router.match("リビングの電気つけて")
        assertThat(m?.arguments?.get("room")).isEqualTo("リビング")
        assertThat(m?.arguments?.get("action")).isEqualTo("turn_on")
    }

    @Test
    fun `bare lights off still works`() {
        val m = router.match("turn the lights off")
        assertThat(m?.arguments?.get("action")).isEqualTo("turn_off")
        assertThat(m?.arguments?.get("room")).isNull()
    }

    @Test
    fun `dim lights sets 30 percent brightness`() {
        val m = router.match("dim the lights")
        assertThat(m?.toolName).isEqualTo("execute_command")
        assertThat(m?.arguments?.get("action")).isEqualTo("set_brightness")
        @Suppress("UNCHECKED_CAST")
        val params = m?.arguments?.get("parameters") as? Map<String, Any?>
        assertThat(params?.get("brightness")).isEqualTo(30)
    }

    @Test
    fun `set brightness 50 percent`() {
        val m = router.match("set lights to 50 percent")
        assertThat(m?.toolName).isEqualTo("execute_command")
        assertThat(m?.arguments?.get("action")).isEqualTo("set_brightness")
    }

    @Test
    fun `japanese brightness percent`() {
        val m = router.match("明るさ80%")
        assertThat(m?.arguments?.get("action")).isEqualTo("set_brightness")
        @Suppress("UNCHECKED_CAST")
        val params = m?.arguments?.get("parameters") as? Map<String, Any?>
        assertThat(params?.get("brightness")).isEqualTo(80)
    }

    @Test
    fun `japanese open app`() {
        val m = router.match("Chromeを開いて")
        assertThat(m?.toolName).isEqualTo("launch_app")
        assertThat(m?.arguments?.get("app_name")).isEqualTo("chrome")
    }
}
