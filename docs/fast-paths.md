# Fast paths

Utterances handled directly by [`FastPathRouter`](../app/src/main/java/com/opensmarthome/speaker/voice/fastpath/FastPathRouter.kt)
without round-tripping through the LLM. Target latency: <200ms from final
STT result to tool execution.

All matchers are language-aware (English + Japanese where applicable).
Order in `DEFAULT_MATCHERS` is significant; precedence comments live next
to the list.

## Catalog

| Matcher | Sample utterances | Tool | Notes |
|---|---|---|---|
| `CancelAllTimersMatcher` | "cancel all timers", "stop timers", "タイマー全部止めて" | `cancel_all_timers` | Precedes TimerMatcher |
| `BroadcastTimerMatcher` | "set a 5 minute timer on all speakers", "全スピーカーで5分タイマー" | `broadcast_timer` | Must precede TimerMatcher — otherwise JP substring `5分タイマー` would hijack to local timer |
| `TimerMatcher` | "set timer for 5 minutes", "5分タイマー" | `set_timer` | EN+JP |
| `AlarmMatcher` | "set an alarm for 7am", "wake me up at 6:30", "7時にアラーム" | `set_timer` (seconds-until-wall-clock) | Rolls past times to tomorrow; sits after TimerMatcher |
| `TimeQueryMatcher` | "what time is it", "今何時" | `get_datetime` | |
| `VolumeMatcher` | "volume up/down", "set volume to 50", "mute", "unmute", "ミュート" | `set_volume` | |
| `ThermostatMatcher` | "set thermostat to 22", "エアコン25度" | `execute_command` device_type=climate | Clamps 10..32°C |
| `FanMatcher` | "fan on", "fan off", "ファンをつけて", "扇風機を消して" | `execute_command` device_type=fan | |
| `TvMatcher` | "TV on", "turn the TV off", "テレビをつけて", "テレビを消して" | `execute_command` device_type=media_player | Physical on/off; play/pause is in MediaControlMatcher |
| `LockMatcher` | "lock the door", "unlock the door", "ドアをロック", "玄関を解錠" | `execute_command` device_type=lock | |
| `CoverMatcher` | "open the blinds", "close the garage", "カーテンを開けて", "ブラインドを閉めて" | `execute_command` device_type=cover | |
| `EverythingOffMatcher` | "turn off everything", "全部消して" | `execute_command` device_type=light off | Conservative — needs explicit "everything"/"all" |
| `LightsMatcher` | "lights on", "電気つけて", "dim the lights", "set brightness 50", "明るさ80%", "bedroom lights off" | `execute_command` device_type=light | Room-scoped variants supported |
| `MediaControlMatcher` | "pause music", "next track", "再生して", "前の曲" | `execute_command` device_type=media_player | |
| `RunRoutineMatcher` | "run X routine", "Xルーチンを実行" | `run_routine` | Requires explicit "routine"/"ルーチン" keyword |
| `SettingsMatcher` | "open wifi settings", "bluetooth settings", "brightness settings", "app list", "Wi-Fiの設定", "明るさ", "音量", "アクセシビリティ", "設定を開いて" | `open_settings_page` | Must precede LaunchAppMatcher so "open wifi settings" isn't launched as an app |
| `OpenUrlMatcher` | "open https://example.com", "open example.com", "go to github.com" | `open_url` | HTTP/HTTPS allow-list; must precede LaunchAppMatcher |
| `LaunchAppMatcher` | "open camera", "launch maps", "Chromeを開いて", "天気アプリ開いて" | `launch_app` | Fuzzy matching via AppNameMatcher (hint-suffix strip + token-set + Levenshtein); skips light/timer/settings keywords |
| `FindDeviceMatcher` | "find my tablet", "デバイスを探して" | `find_device` | Rings + vibrates 10s |
| `GoodnightMatcher` | "goodnight", "I'm going to bed", "おやすみ", "寝ます" | `goodnight` (composite) | Lights off + media pause + cancel timers |
| `ArriveHomeMatcher` | "I'm home", "ただいま" | `arrive_home` (composite) | Lights on + volume 50 |
| `LeaveHomeMatcher` | "I'm leaving", "行ってきます" | `leave_home` (composite) | Lights off + media pause |
| `MorningBriefingMatcher` | "morning briefing", "朝のサマリー" | `morning_briefing` (composite) | Weather + news + calendar |
| `EveningBriefingMatcher` | "evening briefing", "wind down", "夜のサマリー" | `evening_briefing` (composite) | Notifications + calendar + timers |
| `ForecastMatcher` | "weather tomorrow", "this week's forecast", "will it rain tomorrow", "明日の天気", "今週の天気" | `get_forecast` | Precedes WeatherMatcher |
| `WeatherMatcher` | "what's the weather", "今日の天気" | `get_weather` | |
| `NewsMatcher` | "news", "tell me the news", "ニュース" | `get_news` | |
| `CalendarMatcher` | "what's on my calendar today", "do I have any meetings", "今日の予定", "今日のミーティング" | `get_calendar_events` (days_ahead=1) | |
| `ClearNotificationsMatcher` | "clear notifications", "dismiss all notifications", "通知を消して" | `clear_notifications` | Precedes ListNotifications |
| `ListNotificationsMatcher` | "show notifications", "what notifications do I have", "通知一覧" | `list_notifications` | |
| `LocationMatcher` | "where am I", "what's my location", "ここはどこ", "現在地を教えて" | `get_location` | Distinct from FindDeviceMatcher |
| `ListMemoryMatcher` | "what do you remember", "覚えていること" | `list_memory` | |
| `ListDevicesMatcher` | "list my devices", "デバイス一覧" | `get_devices_by_type` (defaults to lights) | |
| `ListTimersMatcher` | "list timers", "what timers do I have", "タイマー一覧" | `get_timers` | Ordered after CancelAllTimersMatcher so "cancel all timers" still wins |
| `DeviceHealthMatcher` | "system status", "device health", "storage space", "memory free", "システム状態", "診断", "ストレージ残量", "メモリ空き" | `get_device_health` | Token-scoped so unrelated utterances pass through |
| `BatteryMatcher` | "what's my battery", "battery level", "バッテリー残量", "電池残量" | (speak-only, BatteryMonitor) | Direct read; speaks % + charging state |
| `LockScreenMatcher` | "lock the screen", "lock the tablet", "screen off", "画面をロック", "スクリーンロック" | `lock_screen` | Requires Device Admin grant |
| `ListPeersMatcher` | "list nearby speakers", "who's on the network", "近くのスピーカー", "スピーカー一覧" | `list_peers` | Narrow tokens; sits ahead of Broadcast matchers |
| `BroadcastGroupMatcher` | "broadcast X to kitchen", "キッチンに X ってアナウンス" | `broadcast_tts` (with group) | Routes via SpeakerGroupRepository; must precede BroadcastTtsMatcher |
| `BroadcastTtsMatcher` | "broadcast X to all speakers", "tell all speakers X", "全スピーカーにアナウンス：X" | `broadcast_tts` | Captures the message; fans out to discovered mDNS peers |
| `HandoffMatcher` | "move this to the kitchen speaker", "send this to bedroom", "キッチンにハンドオフ" | `handoff_session` | Captures target peer name; replaces local history on the target |
| `DatetimeMatcher` | "what's today's date", "今日は何日" | `get_datetime` | |
| `GreetingMatcher` | "thanks", "hello", "ありがとう", "おはよう", "ごめん" | (speak-only) | Canned reply, no tool |
| `HelpMatcher` | "help", "what can you do", "できることを教えて" | (speak-only) | Capability summary mentions timers, lights, weather, news, routines, memory, skills |

## Adding a new matcher

1. Append the `object` to [`FastPathMatchers.kt`](../app/src/main/java/com/opensmarthome/speaker/voice/fastpath/FastPathMatchers.kt)
2. Add it to `DEFAULT_MATCHERS` in `FastPathRouter.kt` respecting precedence
3. Add tests in `FastPathRouterTest.kt`
4. Run `./gradlew testDebugUnitTest --tests "com.opensmarthome.speaker.voice.fastpath.*"`
5. Update this catalog
