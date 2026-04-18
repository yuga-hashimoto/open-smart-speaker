# State of the Project

_Snapshot: 2026-04-17._ A maintainer's stake in the ground: what actually works
today, what is scaffolding, and what has not been touched. Updated when the
picture shifts meaningfully — not on every PR.

## What the app is

OpenDash is an Android tablet app that behaves as a smart speaker and
an on-device LLM agent with OpenClaw-class tool use. The voice pipeline — wake
word, STT, routing, TTS — runs on-device wherever possible, and the default
assistant provider is a local LiteRT-LM model. Cloud providers exist behind the
same abstraction but are opt-in; nothing in the happy path requires network
access beyond smart-home bridges the user chooses to configure.

## Feature matrix

Legend: ✅ shipped · 🟡 scaffolding / partial · ❌ not started · 🚫 won't do

### Voice pipeline

| Feature | Status | Notes |
|---|---|---|
| Wake word (Vosk) | ✅ | Default always-on path via foreground service. |
| Android STT (`SpeechRecognizer`) | ✅ | Primary recogniser; fast on Pixel-class tablets. |
| Offline STT (Whisper / Vosk) | 🟡 | `SttProvider` interface + adapters exist; JNI backend not wired. |
| VAD parameters exposed | ✅ | Silence timeout, min-speech tunables in Settings. |
| Silero VAD backend | 🟡 | Scaffold only — falls back to energy-threshold VAD. |
| Android TTS | ✅ | Default output; locale-aware. |
| Piper neural TTS | 🟡 | Scaffold (`TtsProvider` impl); no bundled voices yet. |

### Assistant + tools

| Feature | Status | Notes |
|---|---|---|
| Local LLM via LiteRT-LM | ✅ | On-device Gemma-class model, hardware-aware init. |
| `AssistantProvider` abstraction + Auto routing | ✅ | Embedded · OpenAI-compatible · OpenClaw. |
| 50+ LLM-callable tools | ✅ | Catalog in [tools.md](tools.md). |
| Fast-path router (30+ matchers) | ✅ | Sub-200ms deterministic path; see [fast-paths.md](fast-paths.md). |
| Skills (25+ bundled + install-from-URL) | ✅ | `SKILL.md` format + skill registry; see [skills.md](skills.md). |
| Routines (Room-backed) | ✅ | Multi-step chains, persistent. |
| Memory (Room + TF-IDF) | ✅ | `remember` / `recall` / `semantic_memory_search`. |
| RAG (Room + chunked retrieval) | ✅ | TextChunker + TF-IDF over stored docs. |
| Context compaction | ✅ | `ContextCompactor` trims long histories. |
| Proactive suggestions | ✅ | Time + rule based (morning / evening briefings). |
| Agent plan executor | ✅ | Multi-tool chaining via `PlanExecutor`. |

### UI + UX

| Feature | Status | Notes |
|---|---|---|
| Ambient / Home / Settings / Dashboard screens | ✅ | Compose + Material 3, tablet-first. |
| Chat screen + VoiceOrb | ✅ | Streaming rendering + state indicator. |
| Permission onboarding | ✅ | `PermissionCatalog` + guided flow. |
| Dynamic launcher shortcuts | ✅ | "Run morning routine" etc. pinnable. |
| Media queue UI (source picker) | ✅ | Pick-and-play via HA media_player. |
| Queue-by-track media control | 🚫 | HA does not expose per-track queue; out of scope. |
| Idle wattage measurement UI | ❌ | Planned but not started. |
| UI internationalisation | ✅ | Six shipped locales (en/ja/es/fr/de/zh-CN) + `LocaleManager` runtime override on Android 13+. See [i18n.md](i18n.md). |

### Device + system integration

| Feature | Status | Notes |
|---|---|---|
| Smart home: HA / SwitchBot / Matter / MQTT | ✅ | `DeviceProvider` abstraction across all four. |
| Accessibility service (tablet control) | ✅ | `read`, `tap_by_text`, `scroll`, `type` verbs. |
| Notification read + reply | ✅ | `list_notifications`, `clear_notifications`, reply. |
| Device admin `lock_screen` | ✅ | Requires user-enabled device-admin grant. |
| Battery saver + thermal throttle | ✅ | Pipeline self-regulates under load. |

### Multi-room (Phase 17)

| Feature | Status | Notes |
|---|---|---|
| mDNS discovery + HMAC NDJSON bus | ✅ | Broadcast TTS, timer fan-out, session handoff, groups, pairing fingerprint. |
| WebSocket upgrade for bus | ❌ | NDJSON is the shipped transport; WS deferred. |
| Camera QR pair | 🚫 | Would add camera dep; word-phrase pairing ships instead. |
| Media handoff across speakers | 🟡 | `session_handoff` moves conversation only — not audio streams. |

### Quality + validation

| Feature | Status | Notes |
|---|---|---|
| Unit tests | ✅ | ~990 tests (JUnit 5 / MockK / Turbine / Truth). |
| E2E / UI tests | ❌ | None yet. |
| Real-device smoke test | ❌ | Checklist exists at [real-device-smoke-test.md](real-device-smoke-test.md); never executed end-to-end. |

## What you can do on a real device today

The short user stories below are all code-complete and unit-tested — but see
the "Open questions" section on what hasn't been empirically confirmed on
hardware.

- **Say "morning briefing"** — proactive rule fires the briefing bundle
  (weather, calendar, unread notifications) through the fast path.
- **Say "broadcast dinner is ready to all speakers"** — mDNS bus finds peers
  on the LAN, HMAC-signs the NDJSON message, every paired tablet speaks it.
- **Tap a pinned "Run morning routine" shortcut** — dynamic launcher shortcut
  triggers the Room-persisted routine without opening the app.
- **Say "turn on the living room lights"** — fast-path matcher resolves the
  device against HA / SwitchBot / Matter / MQTT without an LLM round-trip.
- **Say "remember that my daughter's birthday is June 3"** — memory tool
  writes to Room, `semantic_memory_search` recalls it later.
- **Say "install skill from https://..."** — skill registry fetches, parses
  the `SKILL.md`, registers tools, and surfaces the new skill in `<available_skills>`.
- **Say "read the notification from Slack and reply 'on my way'"** — a11y +
  notification-listener handle the read + reply without the user touching
  the screen.
- **Ask an open question that needs reasoning** — router escalates to the
  local LiteRT-LM model; heavy tasks can route to OpenClaw/Hermes if
  configured.

## Where to start reading code

- `app/src/main/java/com/opendash/app/voice/pipeline/VoicePipeline.kt`
  — the main loop (wake → STT → route → TTS).
- `app/src/main/java/com/opendash/app/assistant/router/ConversationRouter.kt`
  — provider selection (Embedded / OpenAI-compat / OpenClaw / Auto).
- `app/src/main/java/com/opendash/app/tool/CompositeToolExecutor.kt`
  — tool dispatch and usage stats wiring.
- `app/src/main/java/com/opendash/app/voice/fastpath/FastPathRouter.kt`
  — sub-200ms deterministic matchers (24+).
- `app/src/main/java/com/opendash/app/multiroom/` — Phase 17 mDNS
  discovery + HMAC NDJSON message bus.
- `app/src/main/java/com/opendash/app/a11y/OpenDashA11yService.kt`
  — tablet-control verbs (read / tap / scroll / type).

Start at `VoicePipeline`, follow the route into `ConversationRouter`, and let
`CompositeToolExecutor` take you into specific tools. For deterministic paths,
`FastPathRouter` is the short-circuit that sits before the LLM.

## Open questions and design tensions

Honest list of places where the picture is messier than the matrix implies.

- **Offline stack completion (Phase 16) is incomplete.** Whisper STT, Vosk STT
  and Piper TTS all have provider-interface scaffolding but the native JNI
  layer is not wired. Closing this needs native deps we have not yet asked
  the user to green-light under the "External Service Review" rule (even for
  bundled native code, we want explicit sign-off).
- **Multi-room WebSocket upgrade is deferred.** NDJSON over TCP is the happy
  path today. WebSocket was scoped but not prioritised because NDJSON works
  and adds one dependency less.
- **Media handoff across speakers is stubbed.** `session_handoff` moves the
  conversation context between tablets but does _not_ move audio streams or
  HA media-player state. This is called out because "handoff" is a loaded
  word — today it only means "continue talking to me on the other tablet."
- **No real-device validation has happened.** All ~990 unit tests are green
  and lint is clean, but the [real-device smoke test](real-device-smoke-test.md)
  checklist has never been run end-to-end on hardware. Code correctness is
  evidenced; behavioural correctness on a physical tablet is not.
- **No E2E or UI tests.** Coverage is strong at the unit level but there is
  no Espresso / Compose-UI / Macrobenchmark layer. Adding one is a roadmap
  item, not a reflexive "we'll get to it."
- **External service review debt.** Several providers (OpenAI-compatible,
  OpenClaw gateway, HA cloud) require user-supplied credentials and are
  opt-in, but the onboarding flow does not yet gate them behind an explicit
  "you are about to send data off-device" confirmation on first use.

## Updating this page

When the state of a feature genuinely moves — shipped, un-shipped, scope cut,
scope added — update the matrix and bump the snapshot date at the top. Do not
update it for every PR; this is a trailing indicator, not a changelog.
