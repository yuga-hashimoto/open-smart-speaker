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
    fun `where am I calls get_location`() {
        val m = router.match("where am I")
        assertThat(m?.toolName).isEqualTo("get_location")
    }

    @Test
    fun `whats my location fast-path`() {
        val m = router.match("what's my location")
        assertThat(m?.toolName).isEqualTo("get_location")
    }

    @Test
    fun `japanese where am I`() {
        val m = router.match("ここはどこ")
        assertThat(m?.toolName).isEqualTo("get_location")
    }

    @Test
    fun `japanese current location`() {
        val m = router.match("現在地を教えて")
        assertThat(m?.toolName).isEqualTo("get_location")
    }

    @Test
    fun `where am I does not collide with find device`() {
        // FindDeviceMatcher fires on "find/where is my phone/tablet/device".
        // "where am I" must NOT route to find_device.
        val m = router.match("where am I")
        assertThat(m?.toolName).isNotEqualTo("find_device")
    }

    @Test
    fun `weather tomorrow routes to get_forecast`() {
        val m = router.match("what's the weather tomorrow")
        assertThat(m?.toolName).isEqualTo("get_forecast")
    }

    @Test
    fun `weather this week routes to get_forecast`() {
        val m = router.match("weather this week")
        assertThat(m?.toolName).isEqualTo("get_forecast")
    }

    @Test
    fun `forecast for the week`() {
        val m = router.match("forecast for the week")
        assertThat(m?.toolName).isEqualTo("get_forecast")
    }

    @Test
    fun `will it rain tomorrow`() {
        val m = router.match("will it rain tomorrow")
        assertThat(m?.toolName).isEqualTo("get_forecast")
    }

    @Test
    fun `japanese tomorrow weather routes to forecast`() {
        val m = router.match("明日の天気")
        assertThat(m?.toolName).isEqualTo("get_forecast")
    }

    @Test
    fun `japanese week weather routes to forecast`() {
        val m = router.match("今週の天気")
        assertThat(m?.toolName).isEqualTo("get_forecast")
    }

    @Test
    fun `today weather still routes to get_weather`() {
        // Precedence guard: "weather today" is the present-tense variant.
        val m = router.match("what's the weather today")
        assertThat(m?.toolName).isEqualTo("get_weather")
    }

    @Test
    fun `japanese today weather still routes to get_weather`() {
        val m = router.match("今日の天気")
        assertThat(m?.toolName).isEqualTo("get_weather")
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

    @Test
    fun `morning briefing fires and speaks confirmation`() {
        val m = router.match("morning briefing")
        assertThat(m?.toolName).isEqualTo("morning_briefing")
        assertThat(m?.spokenConfirmation?.lowercase()).contains("morning")
    }

    @Test
    fun `good morning briefing also fires`() {
        val m = router.match("good morning briefing")
        assertThat(m?.toolName).isEqualTo("morning_briefing")
    }

    @Test
    fun `morning summary fires the composite`() {
        val m = router.match("morning summary")
        assertThat(m?.toolName).isEqualTo("morning_briefing")
    }

    @Test
    fun `japanese morning briefing`() {
        val m = router.match("朝のサマリー")
        assertThat(m?.toolName).isEqualTo("morning_briefing")
    }

    @Test
    fun `evening briefing fires and speaks confirmation`() {
        val m = router.match("evening briefing")
        assertThat(m?.toolName).isEqualTo("evening_briefing")
        assertThat(m?.spokenConfirmation?.lowercase()).contains("evening")
    }

    @Test
    fun `wind down fires evening briefing`() {
        val m = router.match("wind down")
        assertThat(m?.toolName).isEqualTo("evening_briefing")
    }

    @Test
    fun `japanese evening briefing`() {
        val m = router.match("夜のサマリー")
        assertThat(m?.toolName).isEqualTo("evening_briefing")
    }

    @Test
    fun `morning briefing precedes weather`() {
        // "morning briefing" must NOT route to get_weather even though the
        // composite runs weather under the hood.
        val m = router.match("morning briefing")
        assertThat(m?.toolName).isNotEqualTo("get_weather")
    }

    @Test
    fun `evening briefing precedes news`() {
        val m = router.match("evening briefing")
        assertThat(m?.toolName).isNotEqualTo("get_news")
    }

    @Test
    fun `fan on fast-path`() {
        val m = router.match("fan on")
        assertThat(m?.toolName).isEqualTo("execute_command")
        assertThat(m?.arguments?.get("device_type")).isEqualTo("fan")
        assertThat(m?.arguments?.get("action")).isEqualTo("turn_on")
    }

    @Test
    fun `turn the fan off fast-path`() {
        val m = router.match("turn the fan off")
        assertThat(m?.arguments?.get("device_type")).isEqualTo("fan")
        assertThat(m?.arguments?.get("action")).isEqualTo("turn_off")
    }

    @Test
    fun `japanese fan on`() {
        val m = router.match("扇風機をつけて")
        assertThat(m?.arguments?.get("device_type")).isEqualTo("fan")
        assertThat(m?.arguments?.get("action")).isEqualTo("turn_on")
    }

    @Test
    fun `japanese fan off`() {
        val m = router.match("ファンを消して")
        assertThat(m?.arguments?.get("device_type")).isEqualTo("fan")
        assertThat(m?.arguments?.get("action")).isEqualTo("turn_off")
    }

    @Test
    fun `fan matcher does not steal lights off`() {
        // Precedence guard — "lights off" must still route to light.
        val m = router.match("turn the lights off")
        assertThat(m?.arguments?.get("device_type")).isEqualTo("light")
    }

    @Test
    fun `tv on fast-path`() {
        val m = router.match("TV on")
        assertThat(m?.arguments?.get("device_type")).isEqualTo("media_player")
        assertThat(m?.arguments?.get("action")).isEqualTo("turn_on")
    }

    @Test
    fun `turn the tv off`() {
        val m = router.match("turn the TV off")
        assertThat(m?.arguments?.get("device_type")).isEqualTo("media_player")
        assertThat(m?.arguments?.get("action")).isEqualTo("turn_off")
    }

    @Test
    fun `television long form`() {
        val m = router.match("turn the television on")
        assertThat(m?.arguments?.get("device_type")).isEqualTo("media_player")
        assertThat(m?.arguments?.get("action")).isEqualTo("turn_on")
    }

    @Test
    fun `japanese tv on`() {
        val m = router.match("テレビをつけて")
        assertThat(m?.arguments?.get("device_type")).isEqualTo("media_player")
        assertThat(m?.arguments?.get("action")).isEqualTo("turn_on")
    }

    @Test
    fun `japanese tv off`() {
        val m = router.match("テレビを消して")
        assertThat(m?.arguments?.get("device_type")).isEqualTo("media_player")
        assertThat(m?.arguments?.get("action")).isEqualTo("turn_off")
    }

    @Test
    fun `lock the door fast-path`() {
        val m = router.match("lock the door")
        assertThat(m?.toolName).isEqualTo("execute_command")
        assertThat(m?.arguments?.get("device_type")).isEqualTo("lock")
        assertThat(m?.arguments?.get("action")).isEqualTo("lock")
    }

    @Test
    fun `unlock the door fast-path`() {
        val m = router.match("unlock the door")
        assertThat(m?.arguments?.get("device_type")).isEqualTo("lock")
        assertThat(m?.arguments?.get("action")).isEqualTo("unlock")
    }

    @Test
    fun `japanese door lock`() {
        val m = router.match("ドアをロックして")
        assertThat(m?.arguments?.get("device_type")).isEqualTo("lock")
        assertThat(m?.arguments?.get("action")).isEqualTo("lock")
    }

    @Test
    fun `japanese door unlock`() {
        val m = router.match("玄関を解錠")
        assertThat(m?.arguments?.get("device_type")).isEqualTo("lock")
        assertThat(m?.arguments?.get("action")).isEqualTo("unlock")
    }

    @Test
    fun `lock the cookies does not match`() {
        val m = router.match("lock the cookies")
        assertThat(m).isNull()
    }

    @Test
    fun `open the blinds fast-path`() {
        val m = router.match("open the blinds")
        assertThat(m?.toolName).isEqualTo("execute_command")
        assertThat(m?.arguments?.get("device_type")).isEqualTo("cover")
        assertThat(m?.arguments?.get("action")).isEqualTo("open_cover")
    }

    @Test
    fun `close the garage fast-path`() {
        val m = router.match("close the garage")
        assertThat(m?.arguments?.get("device_type")).isEqualTo("cover")
        assertThat(m?.arguments?.get("action")).isEqualTo("close_cover")
    }

    @Test
    fun `japanese open curtains`() {
        val m = router.match("カーテンを開けて")
        assertThat(m?.arguments?.get("device_type")).isEqualTo("cover")
        assertThat(m?.arguments?.get("action")).isEqualTo("open_cover")
    }

    @Test
    fun `japanese close blinds`() {
        val m = router.match("ブラインドを閉めて")
        assertThat(m?.arguments?.get("device_type")).isEqualTo("cover")
        assertThat(m?.arguments?.get("action")).isEqualTo("close_cover")
    }

    @Test
    fun `open the camera still goes to launch_app`() {
        val m = router.match("open the camera")
        assertThat(m?.toolName).isEqualTo("launch_app")
    }

    // --- DeviceHealthMatcher ---

    @Test
    fun `system status routes to get_device_health`() {
        val m = router.match("System status")
        assertThat(m?.toolName).isEqualTo("get_device_health")
        assertThat(m?.arguments).isEmpty()
        assertThat(m?.spokenConfirmation).isNull()
    }

    @Test
    fun `device health routes to get_device_health`() {
        val m = router.match("Device health")
        assertThat(m?.toolName).isEqualTo("get_device_health")
    }

    @Test
    fun `how is the device running`() {
        val m = router.match("How is the device running?")
        assertThat(m?.toolName).isEqualTo("get_device_health")
    }

    @Test
    fun `how much storage`() {
        val m = router.match("How much storage?")
        assertThat(m?.toolName).isEqualTo("get_device_health")
    }

    @Test
    fun `storage available`() {
        val m = router.match("storage available")
        assertThat(m?.toolName).isEqualTo("get_device_health")
    }

    @Test
    fun `memory free`() {
        val m = router.match("memory free")
        assertThat(m?.toolName).isEqualTo("get_device_health")
    }

    @Test
    fun `japanese system status`() {
        val m = router.match("システム状態")
        assertThat(m?.toolName).isEqualTo("get_device_health")
    }

    @Test
    fun `japanese diagnostic`() {
        val m = router.match("診断")
        assertThat(m?.toolName).isEqualTo("get_device_health")
    }

    @Test
    fun `japanese storage remaining`() {
        val m = router.match("ストレージの残り")
        assertThat(m?.toolName).isEqualTo("get_device_health")
    }

    @Test
    fun `japanese memory free space`() {
        val m = router.match("メモリの空き")
        assertThat(m?.toolName).isEqualTo("get_device_health")
    }

    @Test
    fun `device health matcher does not fire on storage room`() {
        // Isolate to prove DeviceHealthMatcher itself ignores unrelated "storage".
        val match = DeviceHealthMatcher.tryMatch("storage room")
        assertThat(match).isNull()
    }

    @Test
    fun `device health matcher does not fire on what time is it`() {
        // Isolate so the router's DatetimeMatcher doesn't mask the behavior.
        val match = DeviceHealthMatcher.tryMatch("what time is it")
        assertThat(match).isNull()
    }
}
