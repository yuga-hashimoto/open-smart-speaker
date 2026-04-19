# OpenDash

**Turn any Android tablet into a voice-first smart-home device with a local AI brain.**

Think "Alexa without the cloud, OpenClaw-level autonomy, all on-device."

Say a wake word, ask anything, and the agent responds with voice, controls smart-home
devices, runs timers, fetches weather, reads your notifications, or follows user-taught
skills — all without a network call unless you explicitly ask for one.

---

## Why this project

Existing smart speakers force you to choose between:

- **Cloud assistants** (Alexa / Google Home / Siri) — great UX but record your voice,
  lock you into ecosystems, and die the moment the vendor's servers do.
- **Power-user setups** (Home Assistant + ESPHome + Whisper + Piper) — private, but
  require stitching 5 services together to match basic Alexa features.
- **Agent frameworks** (OpenClaw, Hermes) — powerful on a laptop, unusable on a
  kitchen countertop tablet.

OpenDash is all three at once, on a single Android tablet you already own.

---

## What it does today

### Smart-home device ("Alexa feel")
- Custom wake word (Vosk) with always-on foreground service
- Sub-200ms fast-path for 40+ common intents (see full catalog below) — lights,
  volume, timers, alarms, weather, forecast, calendar, notifications, location,
  find-device, briefings, routines, app launch (fuzzy), media control, presence
  (`"I'm home"` / `"I'm leaving"`), goodnight, device health, battery, lock
  screen, open URL, Settings deep-links, multi-room broadcast + handoff
- Voice-first UI: breathing `VoiceOrb` shows pipeline state (listening / thinking /
  speaking) with audio-level reactive scaling
- Error recovery copy: `"I didn't catch that. Try again?"` not `"list index out of range"`
- Filler phrases ("Got it", "One moment") while the LLM is working
- Audio focus handling, barge-in, continuous conversation mode
- Battery-saver + thermal-throttle aware; model-download resume over flaky Wi-Fi

<details>
<summary><strong>Fast-path catalog (click to expand — 40+ matchers)</strong></summary>

Utterances handled directly by [`FastPathRouter`](app/src/main/java/com/opendash/app/voice/fastpath/FastPathRouter.kt) without round-tripping through the LLM (<200ms target). All matchers are EN + JA aware. Ordering in `DEFAULT_MATCHERS` is significant.

| Matcher | Sample utterances | Tool |
|---|---|---|
| `BroadcastCancelTimerMatcher` | "cancel timers on all speakers", "全スピーカーのタイマーをキャンセル" | `broadcast_cancel_timer` |
| `CancelAllTimersMatcher` | "cancel all timers", "タイマー全部止めて" | `cancel_all_timers` |
| `BroadcastTimerMatcher` | "set a 5 minute timer on all speakers", "全スピーカーで5分タイマー" | `broadcast_timer` |
| `TimerMatcher` | "set timer for 5 minutes", "5分タイマー" | `set_timer` |
| `AlarmMatcher` | "wake me up at 6:30", "7時にアラーム" | `set_timer` (wall-clock) |
| `TimeQueryMatcher` | "what time is it", "今何時" | `get_datetime` |
| `VolumeMatcher` | "volume up/down", "set volume to 50", "mute", "ミュート" | `set_volume` |
| `ThermostatMatcher` | "set thermostat to 22", "エアコン25度" | `execute_command` climate |
| `FanMatcher` | "fan on/off", "扇風機を消して" | `execute_command` fan |
| `TvMatcher` | "TV on/off", "テレビをつけて" | `execute_command` media_player |
| `LockMatcher` | "lock/unlock the door", "ドアをロック" | `execute_command` lock |
| `CoverMatcher` | "open the blinds", "カーテンを開けて" | `execute_command` cover |
| `EverythingOffMatcher` | "turn off everything", "全部消して" | `execute_command` light off |
| `LightsMatcher` | "lights on", "dim the lights", "明るさ80%", "bedroom lights off" | `execute_command` light |
| `MediaControlMatcher` | "pause music", "next track", "再生して" | `execute_command` media_player |
| `RunRoutineMatcher` | "run X routine", "Xルーチンを実行" | `run_routine` |
| `SettingsMatcher` | "open wifi settings", "Wi-Fiの設定", "アクセシビリティ" | `open_settings_page` |
| `OpenUrlMatcher` | "open https://example.com", "go to github.com" | `open_url` |
| `LaunchAppMatcher` | "open camera", "Chromeを開いて", "天気アプリ開いて" | `launch_app` |
| `FindDeviceMatcher` | "find my tablet", "デバイスを探して" | `find_device` |
| `GoodnightMatcher` | "goodnight", "おやすみ" | `goodnight` (composite) |
| `ArriveHomeMatcher` | "I'm home", "ただいま" | `arrive_home` (composite) |
| `LeaveHomeMatcher` | "I'm leaving", "行ってきます" | `leave_home` (composite) |
| `MorningBriefingMatcher` | "morning briefing", "朝のサマリー" | `morning_briefing` |
| `EveningBriefingMatcher` | "evening briefing", "夜のサマリー" | `evening_briefing` |
| `ForecastMatcher` | "weather tomorrow", "明日の天気" | `get_forecast` |
| `WeatherMatcher` | "what's the weather", "今日の天気" | `get_weather` |
| `NewsMatcher` | "news", "ニュース" | `get_news` |
| `CalendarMatcher` | "what's on my calendar today", "今日の予定" | `get_calendar_events` |
| `ClearNotificationsMatcher` | "clear notifications", "通知を消して" | `clear_notifications` |
| `ListNotificationsMatcher` | "show notifications", "通知一覧" | `list_notifications` |
| `LocationMatcher` | "where am I", "現在地を教えて" | `get_location` |
| `ListMemoryMatcher` | "what do you remember", "覚えていること" | `list_memory` |
| `ListDevicesMatcher` | "list my devices", "デバイス一覧" | `get_devices_by_type` |
| `ListTimersMatcher` | "list timers", "タイマー一覧" | `get_timers` |
| `DeviceHealthMatcher` | "system status", "storage space", "システム状態", "ストレージ残量" | `get_device_health` |
| `BatteryMatcher` | "battery level", "バッテリー残量" | BatteryMonitor (speak-only) |
| `LockScreenMatcher` | "lock the tablet", "画面をロック" | `lock_screen` |
| `ListPeersMatcher` | "list nearby speakers", "近くのスピーカー" | `list_peers` |
| `BroadcastGroupMatcher` | "broadcast X to kitchen", "キッチンに X ってアナウンス" | `broadcast_tts` (group) |
| `BroadcastTtsMatcher` | "tell all speakers X", "全スピーカーにアナウンス：X" | `broadcast_tts` |
| `HandoffMatcher` | "move this to the kitchen speaker", "キッチンにハンドオフ" | `handoff_session` |
| `FlipCoinMatcher` | "flip a coin", "コインを投げて" | `flip_coin` |
| `DatetimeMatcher` | "what's today's date", "今日は何日" | `get_datetime` |
| `GreetingMatcher` | "thanks", "ありがとう", "おはよう" | speak-only |
| `HelpMatcher` | "help", "what can you do", "できることを教えて" | speak-only |

Full notes with precedence rules in [docs/fast-paths.md](docs/fast-paths.md).

</details>

### Local AI agent ("OpenClaw feel")
- On-device LLM via **LiteRT-LM** (Gemma 3n / 4 family) with GPU→CPU fallback and
  hardware-tier-tuned context size / thread count
- Pluggable chat templates (Gemma / Qwen / Llama3 / ChatML)
- **ReAct agent loop** with multi-tool chaining (`AgentPlan`), up to 10 tool rounds
- Context compaction keeps long conversations coherent
- Device state auto-injected into every prompt (`<device_state>`)
- **Skills system** — drop a `SKILL.md` into `assets/skills/<name>/` or install one
  at runtime via `install_skill_from_url` (OpenClaw-style). 36 bundled skills:

  <details>
  <summary><strong>Bundled skills catalog (click to expand)</strong></summary>

  | Skill | Purpose |
  |---|---|
  | `arrival-home` | Warm welcome on arrival — entry lights, next event, urgent notifications |
  | `bedtime-for-kids` | Family wind-down — broadcast warning, dim lights, pause media, cool-down timer |
  | `bedtime-routine` | Evening wind-down — pause media, dim lights, set alarm, summary |
  | `breathing-exercise` | 4-7-8 breathing guide — four voice-cued cycles |
  | `cooking-assistant` | Recipes, steps, unit conversions, kitchen timers |
  | `cooking-session` | Multi-timer cooking — labeled parallel timers + "dinner is ready" broadcast |
  | `dinner-call` | Broadcast TTS to every speaker + pinned banner |
  | `dog-walk` | Weather cue, 30-min backstop timer, welcome-home prompt |
  | `eye-break` | 20-20-20 screen-break reminder |
  | `focus-mode` | Deep work — quiet alerts, steady light, optional Pomodoro |
  | `gratitude-journal` | Nightly 3-item gratitude prompt stored via `remember` |
  | `guest-mode` | Relax the agent for visitors — silence wake-word + personal data |
  | `home-control` | Smart-home control via HA and other providers |
  | `hydration-reminder` | Low-friction hourly water nudge |
  | `leaving-home` | Send-off — lights out, battery reminder, weather preview |
  | `meditation` | Quiet session — dim warm light, silence notifications, optional bell |
  | `morning-routine` | Wake-up flow — briefing, lights, ambient music, schedule |
  | `movie-night` | Dim lights, pause notifications, queue media |
  | `news-briefing` | Daily news + weather briefing |
  | `party-mode` | Colourful lights, music, volume up, multi-speaker announcements |
  | `pomodoro` | 25/5 cycles with 15-min rest every fourth block |
  | `power-nap` | 20-min nap — dim to 20%, short timer, gentle wake |
  | `quick-note` | Rapid voice-note capture into `notes` memory namespace |
  | `quiet-hours` | Night-time noise cap — low TTS, skip broadcasts |
  | `rainy-day` | Forecast-driven cozy indoor plan suggestion |
  | `reading-time` | Warm light, silence everything else, optional timer |
  | `remind-me-tomorrow` | Dated memory entry surfaced in morning-briefing |
  | `sick-day` | Rest mode — dim lights, low volume, hydration every 2h |
  | `stretch-break` | 5-min guided stretch routine |
  | `study-mode` | Quiet study — dim lights, silence, optional Pomodoro |
  | `task-manager` | Capture/recall to-do items via long-term memory |
  | `travel-mode` | Trip mode — all lights off, cancel timers, goodbye broadcast |
  | `voice-assistant` | General voice interaction rules (loaded every turn) |
  | `wake-up-gently` | Sunrise-style 10-min light ramp + briefing |
  | `where-did-i-put-it` | Search memory for prior "I put X in Y" statements |
  | `workout` | Bright lights, upbeat music, interval timers |

  </details>

- **50+ built-in LLM tools**:
  - System: timer, volume, app launcher, datetime, notifications, calendar,
    contacts, SMS, camera, photos, screen record, device health, location,
    screen reader
  - Info: weather (Open-Meteo), search (DuckDuckGo), news (RSS), web fetch,
    unit converter, calculator, currency converter
  - Memory: `remember`, `recall`, `search_memory`, `semantic_memory_search`, `forget`
  - Documents: RAG ingest + retrieve with TF-IDF scoring
  - Routines: user-defined multi-step workflows
  - Skills: `get_skill`, `list_skills`, `install_skill_from_url`

### Smart-home control
| Protocol | Devices |
|----------|---------|
| **Home Assistant** | Everything HA exposes (lights, climate, covers, media, sensors) |
| **SwitchBot** | Bot, Curtain, Plug, Bulb, Strip/Ceiling Light, Lock, Meter |
| **Matter** | Android Matter API commissioning |
| **MQTT** | Shelly, Tasmota, and any MQTT-discoverable device |

### Tablet control (no root)
- Accessibility service drives: `read_active_screen`, `tap_by_text`, `scroll_screen`, `type_text`
- Notification listener drives: `list_notifications`, `clear_notifications`, `reply_to_notification`
- Fuzzy `launch_app` finds the right app even when the user's wording is loose ("open the weather app")
- Deep-link into Settings, open URLs, pin routines to the home screen as dynamic shortcuts
- Quick Settings **Talk** tile for one-tap voice without the wake word
- Opt-in Device Admin unlocks `lock_screen`; no other policies requested
- Full recipe book in [docs/tablet-control-cookbook.md](docs/tablet-control-cookbook.md)

### Multi-room (`_opendash._tcp.` on port 8421)
- Auto-discover peers via mDNS; pair with a shared HMAC-SHA256 secret
- 4-word **pairing fingerprint** so users verify secrets match without typing them again
- Speaker groups (`kitchen`, `upstairs`, ...) so `broadcast_tts` can target subsets
- Tools: `broadcast_tts`, `broadcast_timer`, `broadcast_cancel_timer`, `broadcast_announcement`, `handoff_session`, `list_peers`
- Quickstart in [docs/multi-room-quickstart.md](docs/multi-room-quickstart.md), full recipe book in [docs/multi-room-cookbook.md](docs/multi-room-cookbook.md), wire format in [docs/multi-room-protocol.md](docs/multi-room-protocol.md)

### Privacy / data
- **All AI runs locally.** The LLM lives on your tablet, not a server.
- **Secrets encrypted** via EncryptedSharedPreferences (AES256-GCM).
- **Network calls minimized** and opt-in. See [SECURITY.md](SECURITY.md).

### Languages
UI translated into English, 日本語, Español, Français, Deutsch, 简体中文 — pick in
Settings → Language (Android 13+) or follow the system locale. Fast-path
matchers and bundled skills respond to EN + JA utterances side-by-side. See
[docs/i18n.md](docs/i18n.md) for the full list and how to contribute a new
locale.

---

## Hardware recommendations

| RAM | Model tier |
|-----|-----------|
| 3-4 GB | Gemma 3n E2B (Q4) ~1.2 GB |
| 4-6 GB | Gemma 3n E2B / Qwen3 1.5B |
| 6-8 GB | Gemma 3n E4B (Q4) ~2.5 GB |
| 8+ GB  | Gemma 4 / Phi-4 Mini |

`HardwareProfile` auto-detects RAM and tunes context / GPU layers / thread count.

---

## Setup (5 minutes)

1. Install the APK (GitHub Releases or `./gradlew assembleDebug`)
2. Grant microphone permission on first launch
3. Model auto-downloads (~2 GB, public HuggingFace)
4. Settings → configure device providers (SwitchBot / MQTT / HA / Matter)
5. Say your wake word and start asking

Optional permissions unlock more tools:
- Calendar → `get_calendar_events`
- Contacts → `search_contacts`
- Location → `get_location`
- Notification access → `list_notifications`, `clear_notifications`, `reply_to_notification`
- Accessibility service → `read_screen`, `read_active_screen`, `tap_by_text`, `scroll_screen`, `type_text`
- Device Admin (opt-in) → `lock_screen` (only force-lock policy requested)
- SMS → `send_sms` (with LLM confirmation prompt)
- Photos → `list_recent_photos`
- Camera → `take_photo`

---

## Development

Kotlin 2.1 + Jetpack Compose + Material 3 + Hilt. Built against SDK 35.

```bash
./gradlew testDebugUnitTest   # ~1000 unit tests
./gradlew assembleDebug       # build APK
./gradlew lint                # Android Lint (baseline at app/lint-baseline.xml)
./gradlew jacocoTestReport    # coverage report → app/build/reports/jacoco/
```

JUnit 5 + MockK + Truth + MockWebServer + Turbine, 80%+ coverage target for
non-UI code. See [CONTRIBUTING.md](CONTRIBUTING.md).

### Architecture

```
app/src/main/java/com/opendash/app/
├── assistant/
│   ├── provider/embedded/   # LiteRT-LM on-device provider
│   ├── provider/openai/     # OpenAI-compatible REST+SSE
│   ├── provider/openclaw/   # OpenClaw WebSocket
│   ├── router/              # 4 routing policies
│   ├── skills/              # SkillRegistry + SkillInstaller (OpenClaw SKILL.md)
│   ├── routine/             # Routine + RoomRoutineStore
│   ├── agent/               # AgentPlan + PlanExecutor
│   ├── proactive/           # Time-based suggestions + rules
│   └── context/             # ContextCompactor + DeviceContextBuilder
├── device/                  # HA / SwitchBot / MQTT / Matter providers
├── voice/
│   ├── pipeline/            # VoicePipeline + ErrorClassifier
│   └── fastpath/            # FastPathRouter (Alexa-style LLM bypass)
├── tool/
│   ├── system/              # timer / volume / notifications / SMS / camera etc
│   ├── info/                # weather / search / news / calc / currency
│   ├── memory/              # persistent KV + TF-IDF semantic search
│   ├── rag/                 # document chunker + retrieval
│   ├── accessibility/       # screen reader service
│   └── analytics/           # usage stats
├── ui/                      # Chat / Dashboard / Ambient / Settings / Home
├── service/                 # Foreground + VoiceInteraction + Boot
└── data/                    # Room + encrypted preferences
```

See [docs/architecture.md](docs/architecture.md) for the deeper dive.

---

## Priority order

Every change in this project advances one of these, in order:

1. **Smart home device feel** — it has to feel like Alexa for everyday use
2. **Local agent capabilities** — OpenClaw-style, on-device
3. **UX polish** — ambient, onboarding, error recovery
4. **Hybrid gateway** — connect to external OpenClaw / Hermes when needed
5. **Refactor / quality** — code, performance, security
6. **OSS project health** — docs, CI, community

See [docs/roadmap.md](docs/roadmap.md) for the full per-item list.

---

## Inspiration / credit

- **OpenClaw** — the agent framework we bring to Android
- **Ava** — voice state machine + wake ripple UX patterns
- **off-grid-mobile-ai** — hardware-aware LLM init + context compaction
- **dash-voice** — tablet-first smart-home UX
- **local-llms-on-android** — LiteRT-LM integration reference

Each of these informed a specific PR; see commit messages for "stolen from ..." notes.

---

## License

MIT
