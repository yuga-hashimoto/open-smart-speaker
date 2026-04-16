# OpenSmartSpeaker

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

OpenSmartSpeaker is all three at once, on a single Android tablet you already own.

---

## What it does today

### Smart-home device ("Alexa feel")
- Custom wake word (Vosk) with always-on foreground service
- Sub-200ms fast-path for common intents: `"lights on"`, `"volume up"`,
  `"5分タイマー"`, `"what time is it"` — LLM bypass via `FastPathRouter`
- Voice-first UI: breathing `VoiceOrb` shows pipeline state (listening / thinking /
  speaking) with audio-level reactive scaling
- Error recovery copy: `"I didn't catch that. Try again?"` not `"list index out of range"`
- Filler phrases ("Got it", "One moment") while the LLM is working
- Audio focus handling, barge-in, continuous conversation mode

### Local AI agent ("OpenClaw feel")
- On-device LLM via **LiteRT-LM** (Gemma 3n / 4 family) with GPU→CPU fallback and
  hardware-tier-tuned context size / thread count
- Pluggable chat templates (Gemma / Qwen / Llama3 / ChatML)
- **ReAct agent loop** with multi-tool chaining (`AgentPlan`), up to 10 tool rounds
- Context compaction keeps long conversations coherent
- Device state auto-injected into every prompt (`<device_state>`)
- **Skills system** — drop a `SKILL.md` into `assets/skills/<name>/` or install one
  at runtime via `install_skill_from_url` (OpenClaw-style)
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

### Privacy / data
- **All AI runs locally.** The LLM lives on your tablet, not a server.
- **Secrets encrypted** via EncryptedSharedPreferences (AES256-GCM).
- **Network calls minimized** and opt-in. See [SECURITY.md](SECURITY.md).

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
- Notification access → `list_notifications`
- Accessibility service → `read_screen`
- SMS → `send_sms` (with LLM confirmation prompt)
- Photos → `list_recent_photos`
- Camera → `take_photo`

---

## Development

Kotlin 2.1 + Jetpack Compose + Material 3 + Hilt. Built against SDK 35.

```bash
./gradlew testDebugUnitTest   # 400+ unit tests
./gradlew assembleDebug       # build APK
./gradlew lint                # Android Lint
```

JUnit 5 + MockK + Truth + MockWebServer, ~80% coverage for non-UI code.
See [CONTRIBUTING.md](CONTRIBUTING.md).

### Architecture

```
app/src/main/java/com/opensmarthome/speaker/
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
