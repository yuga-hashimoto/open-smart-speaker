# Improvement Roadmap (v2)

## Vision
Androidタブレットを **「アレクサ以上 + OpenClaw相当のローカルAIエージェント」** に変える。
スマートホームデバイスとして最高のUXを、完全ローカル・軽量で実現する。

## User Priority Order (この順で改善する)
1. **アレクサなどのスマートホームデバイスとして動くこと** — 即応性、視覚フィードバック、voice-first
2. **OpenClawのようにローカルLLMでagentic** — engine/tool layer 大部分達成、UI統合と軽量化が残
3. **最高のUX** — ambient mode, onboarding, error recovery
4. **OpenClaw / HermesAgent と外部接続** — 重処理は外部 gateway へ
5. **リファクタ** — 不要コード削除、メンテ性、パフォーマンス、セキュリティ
6. **OSSとして発展する体制** — CONTRIBUTING, issue templates, CI

---

## Status Summary

### Done — Agent engine + tool layer
- Phase 1: system prompt, history, tool call parser (JSON/XML/Gemma4), chat templates (Gemma/Qwen/Llama3)
- Phase 2: timer, volume, app launcher, datetime, notifications, calendar, contacts
- Phase 3: weather (Open-Meteo), search (DDG), news (RSS), knowledge, web fetch, unit converter, calculator, currency
- Phase 4: context compaction, user memory (Room), device state injection, datetime awareness
- Phase 5: multi-tool chaining, proactive suggestions, routines (Room), skills (SKILL.md + install), location, screen reader
- Phase 6: SMS, camera skeleton, screen record skeleton, photos, device health, routine persistence, TF-IDF semantic memory, RAG, vision model
- Phase 7 data: permission catalog, persistent tool stats, memory/skill/routine/rag/analytics repos, suggestion state

### Not done (Phase 8+)
- **UI integration** — most repos have no screen on top yet
- **Real-device UX testing** — wake word latency, barge-in, smoothness
- **CameraX / MediaProjection** — skeletons only
- **Smart-home-first feel** — fast-path, ambient polish, first-run

---

## Phase 8 — Priority 1: Smart Home Device Feel
Make it feel like Alexa/Google Home first.

- [x] P8.1: Fast-path command router wired into VoicePipeline — LLM bypass for timers/volume/lights/time/date (EN+JA)
- [x] P8.2: Wake-to-listening latency budget — Span enum now carries per-span budget (WAKE_TO_LISTENING=500ms, FAST_PATH_TO_RESPONSE=200ms, etc.); endSpan() warns via Timber when exceeded; LatencyRecorder.budgetViolations() exposes counts. Priority-1 targets baked into code
- [x] P8.3: VoiceOrb compose component — per-state color + breathing + audio-level scaling (stolen from Ava WakeRippleView + OpenClawSession mic orb)
- [x] P8.4: Ambient home screen — AmbientSnapshotBuilder wired into AmbientViewModel; shows greeting + clock + weather + timer/notification counts + active device list; TimerManager/NotificationProvider promoted to Hilt singletons so tool invocations and ambient view share state
- [x] P8.5: Barge-in verified via unit tests (interruptAndListen + stopSpeaking both halt TTS)
- [x] P8.6: Filler phrases (existing FillerPhrases object — JP/EN, initial + wait timing, user-toggleable)
- [x] P8.7: ErrorClassifier — 7 categories (no-provider / STT / timeout / network / permission / tool / unknown) with spoken-friendly copy + retry flag (stolen Ava pattern)
- [x] P8.8: Tablet-first landscape layout — new `isExpandedLandscape()` helper (≥600dp wide + landscape); HomeScreen and AmbientScreen switch to two-column split on tablet landscape (clock/weather left, devices/status right). Touch targets already 48dp+ via IconButton/FAB defaults; NightClockOverlay + night-mode control drawer already shipped

## Phase 9 — Priority 2: Surface existing OpenClaw engine capabilities in UI
- [x] P9.1: Settings → Skills manager (SkillsViewModel + SkillsScreen: list, install-from-URL, delete)
- [x] P9.2: Settings → Routines manager (RoutinesViewModel + RoutinesScreen: list, delete, action preview)
- [x] P9.3: Settings → Memory browser (MemoryViewModel + MemoryScreen: search, delete, clear-all)
- [x] P9.4: Settings → Documents / RAG (DocumentsViewModel + DocumentsScreen: ingest card, list with chunk counts, delete)
- [x] P9.5: Settings → Analytics dashboard (AnalyticsViewModel + AnalyticsScreen: summary card, per-tool success rates, reset)
- [x] P9.6: Settings → Custom system prompt editor (SystemPromptViewModel + SystemPromptScreen)
- [x] P9.7: Settings → Permissions checklist (PermissionsViewModel + PermissionsScreen, per-row 'Open settings' deep-link)
- [x] P9.8: SuggestionBubble compose component for Home (wired to SuggestionState in next cycle)

## Phase 10 — Priority 3: UX polish
- [x] P10.1: First-run permission walkthrough — OnboardingScreen + OnboardingViewModel; shows after model download before ModeScaffold when SETUP_COMPLETED=false; uses PermissionRepository rows with rationale + "Grant" deep-link; "Skip" / "Get started" both flip flag
- [x] P10.2: Voice-controlled tour — HelpMatcher added to fast-path; "help" / "what can you do" / "できることを教えて" return a canned capability summary with zero LLM round-trip. FastPathMatch.toolName made nullable for speak-only responses
- [x] P10.3: Offline-first error states — ErrorClassifier.ProviderKind (LOCAL/REMOTE/UNKNOWN); LOCAL provider remaps network-shaped errors to LOCAL_ENGINE, adds model-load patterns; ProviderCapabilities.isLocal propagates from EmbeddedLlmProvider; VoicePipeline passes kind based on active provider
- [x] P10.4: Accessibility pass — VoiceOverlay response + state label now `liveRegion = Polite` so TalkBack announces state changes; SuggestionBubble also `liveRegion = Polite`; decorative icons already correctly pass `contentDescription = null` alongside labeled text
- [x] P10.5: Dark/light mode — OpenSmartSpeakerTheme accepts darkTheme param (defaults to isSystemInDarkTheme()); LightColorScheme added; Material3 surfaces respect system theme; ambient/home keep dark smart-speaker aesthetic via hardcoded Speaker* colors
- [x] P10.6: Music Assistant / media control UI — NowPlayingBar wired to dispatch play/pause/next/prev via DeviceManager.executeCommand; HA media_player service names (media_play/media_pause/media_next_track/media_previous_track); HomeViewModel.dispatchMediaAction; clock tick moved from ViewModel to Composable for testability; HomeViewModelTest covers action wiring

## Phase 11 — Priority 4: Hybrid / External Gateway
- [x] P11.1: HermesAgent protocol adapter — HermesAgentProvider implements AssistantProvider; NDJSON streaming; Bearer token auth; health probe; test coverage with MockWebServer
- [x] P11.2: OpenClawProvider streaming + tool forwarding — send() now aggregates ToolCallRequest from tool_call deltas into Assistant.toolCalls; request payload forwards full parameter schema (type/description/required/enum); capabilities.isLocal=false so ErrorClassifier surfaces network issues honestly
- [x] P11.3: Heavy task hint — HeavyTaskDetector with conservative heuristics (long input, heavy keywords EN+JA, vision request vs local capability). Router policy can consult it when Auto; UI can show escalation hint to user
- [x] P11.4: Unified provider switcher polish — ProvidersScreen + ProvidersViewModel lists registered AssistantProviders with badges (On-device / Streaming / Tools / Vision), active highlighted; tap to call router.selectProvider; ProvidersViewModelTest covers rows + selection

## Phase 12 — Priority 5: Refactor / Quality
- [x] P12.1: Dead code sweep — removed dead `is AndroidTtsProvider` branch in VoicePipeline.applyTtsLanguagePreference (TtsManager is the only injected TextToSpeech, so the direct provider branch was unreachable). Removed corresponding unused import
- [x] P12.2: Camera integration — IntentCameraProvider uses ACTION_IMAGE_CAPTURE via ActivityResultContracts.TakePicture with FileProvider-backed URI; MainActivity registers it in CameraProviderHolder on onCreate so take_photo tool now produces real image bytes (no new dependencies vs CameraX)
- [x] P12.3: MediaProjection integration — MediaProjectionScreenRecorder uses MediaProjection + MediaRecorder + VirtualDisplay to record the display as MP4 in cache; consent requested via ActivityResultContracts.StartActivityForResult; MainActivity registers it in ScreenRecorderHolder on onCreate
- [x] P12.4: SecurePreferences audit — SWITCHBOT_TOKEN moved from plaintext DataStore to SecurePreferences (was leaking); added KEY_SWITCHBOT_TOKEN/SECRET/MQTT_PASSWORD constants; removed silent fallback to plaintext SharedPreferences on keystore failure; deleted dead plaintext secret keys (HA_TOKEN, OPENCLAW_API_KEY, SWITCHBOT_SECRET, MQTT_PASSWORD from PreferenceKeys)
- [x] P12.5: Unified OkHttp with sensible timeouts — TtsManager no longer builds its own client; all HTTP uses NetworkModule singleton (30s connect / 60s read / 30s write)
- [x] P12.6: Coverage report — JaCoCo report via `./gradlew jacocoTestReport` (no new deps, uses AGP built-in). Excludes UI, generated Hilt/Moshi/Room/Compose code, and runs against debug unit tests. HTML + XML outputs under app/build/reports/jacoco/
- [x] P12.7: Android Lint baseline — added `lint { baseline = file("lint-baseline.xml") }` to app/build.gradle.kts. Baseline captures the 77 pre-existing warnings so CI now fails on new issues only. Regenerate with `./gradlew updateLintBaseline`

## Phase 13 — Priority 6: OSS Project Health
- [x] P13.1: CONTRIBUTING.md (priority-order guide, code style, tool/skill authoring)
- [x] P13.2: Issue + PR templates (bug report / feature request / PR template)
- [x] P13.3: CI workflow already exists (gradle test + lint)
- [x] P13.4: Release workflow — tag push builds debug APK and attaches to release
- [x] P13.5: SECURITY.md with threat model + responsible disclosure
- [x] P13.6: CODE_OF_CONDUCT.md (Contributor Covenant 2.1)
- [x] P13.7: README overhaul — value prop, hardware guide, tool list, architecture diagram, inspiration credits
- [x] P13.8: Docs site — docs/index.md landing + topic pages (tools, providers, skills, permissions); docs/_config.yml enables GitHub Pages with jekyll-theme-cayman

## Phase 14 — Priority 1: Smart speaker production gaps
Roadmapがチェック済みでも、実機でアレクサ相当にはならない。以下は実装ゼロ or 薄い:

**Closeout summary** (as of the current session): every P14 item has at least
scaffolding merged. Remaining work is ネイティブ JNI / new model wiring that
would each warrant its own implementation phase:

- **P14.1 offline STT**: provider-selection plumbing + Settings UI done via
  `DelegatingSttProvider` + `OfflineSttStub`. Real whisper.cpp / Vosk JNI = Phase 15.
- **P14.2 VAD**: silence-timeout + min-speech knobs split; Silero VAD backend = Phase 15.
- **P14.3 wake-word UI**: keyword + sensitivity shipped end-to-end. Done.
- **P14.4 media depth**: volume / shuffle / repeat / source-picker bottom sheet shipped. HA integrations don't expose a uniform track queue, so per-track queue UI is out of scope.
- **P14.5 multi-room**: mDNS discover + register + Settings opt-in toggle + reactive
  lifecycle wiring shipped (toggle now starts/stops the server without a service
  restart via `MultiroomLifecycleController`). Broadcast RPC protocol = Phase 15.
- **P14.6 DL resume**: HTTP Range end-to-end with full test coverage. Done.
- **P14.7 smoke-test doc**: real-device checklist shipped (10 scripted steps +
  multi-room section). Done.
- **P14.8 power/thermal**: BatteryMonitor + ThermalMonitor with ambient UI chips
  and VoiceService gating done. Idle wattage measurement still TODO.
- **P14.9 neural TTS**: PiperTtsProvider placeholder + Settings route shipped.
  piper-cpp JNI + voice-model download = Phase 15.

- [ ] P14.1: Offline STT provider — **scaffolding done**: SttProviderType enum (ANDROID / VOSK / WHISPER) + `STT_PROVIDER_TYPE` preference + DelegatingSttProvider that routes by preference at startListening time; OfflineSttStub emits a spoken "coming soon" Error so pipeline's ErrorClassifier surfaces it; Settings UI shows the three options with "coming soon" badges on offline. Actual whisper.cpp / Vosk JNI wiring still TODO. Ref: whisper.cpp, sherpa-onnx, SmolChat-Android
- [ ] P14.2: VAD / endpoint detection — **parameters exposed**: separated `MIN_SPEECH_MS` from `SILENCE_TIMEOUT_MS` (previously both used the same value, which was surprising); new slider under Voice Interaction; AndroidSttProvider wires both into `EXTRA_SPEECH_INPUT_*`. Offline Silero/WebRTC VAD integration still TODO. Ref: sherpa-onnx silero-vad binding
- [x] P14.3: Wake word customization UI — Sensitivity slider (0.0-1.0) in SettingsScreen alongside existing keyword text field; WAKE_WORD_SENSITIVITY preference; VoiceService loads into WakeWordConfig; VoskWakeWordDetector uses sensitivity to gate partial-result matching (threshold 0.5 = partial vs final-only). Unit tests cover the gate. Keyword customization was already shipped
- [x] P14.4: Media deeper control — **all media controls shipped**: volume slider (0-100 %), shuffle toggle, repeat cycle (off→all→one), source/playlist picker bottom sheet (`media_player.select_source`). Per-track queue view out of scope: HA doesn't expose a uniform queue attribute across integrations. Ref: home-assistant/android media controls
- [ ] P14.5: Multi-room broadcast — **discovery + registration + reactive toggle shipped**: MulticastDiscovery @Singleton wraps NsdManager for `_opensmartspeaker._tcp`; async resolveService fills host/port; SystemInfoScreen lifecycle-binds start/stop. Registration: `register(port, instanceName)` / `unregister()` + `registeredName: StateFlow<String?>` advertise this device on DEFAULT_PORT=8421. `MultiroomLifecycleController` wires the `MULTIROOM_BROADCAST_ENABLED` preference through `observe().distinctUntilChanged().collectLatest` so flipping the Settings toggle at runtime starts/stops mDNS register + AnnouncementServer + PeerLivenessTracker without requiring a service restart. Controller is idempotent, mutex-serialized, and crash-safe (onStop exceptions are swallowed). Broadcast protocol handshake (timers, announcements) still TODO. Ref: OVOS message bus
- [x] P14.6: Model download resume — ModelDownloader now sends `Range: bytes=N-` when a `.downloading` temp file exists and appends on 206 Partial Content. Falls back cleanly to full download when server returns 200. Failure path preserves partial file for next retry. Unit tests cover Range header, 206 append, 200-fallback, and post-failure survival
- [x] P14.7: Real-device smoke test checklist — docs/real-device-smoke-test.md with wake→STT→LLM→TTS end-to-end steps + latency targets; 10 scripted steps (cold start → wake latency → fast path → tool call → barge-in → offline → tablet → onboarding → 30-min stability → system info) + power/thermal note + dated-run template
- [ ] P14.8: Power/thermal profile — **battery saver + thermal throttle done**: BatteryMonitor + ThermalMonitor @Singletons; BATTERY_SAVER_ENABLED preference gates both; VoiceService skips wake word on low battery OR WARM/HOT thermal state. Idle wattage measurement + saver UI indicator still TODO
- [ ] P14.9: Neural TTS option — **scaffolding done**: PiperTtsProvider placeholder delegates to AndroidTtsProvider and logs a warning; TtsManager routes `TTS_PROVIDER = "piper"` to it; Settings radio gains "Piper neural (offline) — coming soon" option. Actual piper-cpp JNI + voice-model download flow still TODO. Ref: piper

## Phase 15 — Priority 1: Full tablet control through voice (no root)
タブレットをスマートスピーカー経由で「完璧に使いこなせる」ゴール。AccessibilityService +
NotificationListenerService + Intent-based settings で Root なしに実現する。

Design principle: **never require root**. Any capability reachable via a11y / notification /
device-admin (opt-in) / launcher / intent stays supported; anything that genuinely needs
root goes on a "won't do" list.

**Status:** 13/13 shipped. A11y + NotificationListener + DeviceAdmin skeletons
are live; voice-first tablet control is functionally complete (modulo real-device
smoke testing).

- [x] P15.1: AccessibilityService skeleton — `OpenSmartSpeakerA11yService` +
  `A11yServiceHolder` + Accessibility special-grant in PermissionCatalog (PR #230)
- [x] P15.2: `read_active_screen` tool — BFS node tree dump, markdown output (PR #233)
- [x] P15.3: `tap_by_text` tool — GestureDescription click at matched node centre (PR #238)
- [x] P15.4: `scroll_screen` / swipe tool — GestureDescription-based directional swipe (PR #238)
- [x] P15.5: `type_text` tool — ACTION_SET_TEXT with clipboard+paste fallback (PR #238)
- [x] P15.6: Fuzzy app launcher — AppNameMatcher with hint-strip + token-set + Levenshtein (PR #229)
- [x] P15.7: Settings deep-links — `open_settings_page` tool + SettingsMatcher (PR #231)
- [x] P15.8: Notification reply — `reply_to_notification` via RemoteInput (PR #237)
- [x] P15.9: Quick Settings TileService — one-tap voice session from QS panel (PR #235)
- [x] P15.10: Device admin opt-in — `lock_screen` tool + DeviceAdminReceiver (force-lock only) +
  LockScreenMatcher. Opt-in: user grants in Settings → Security → Device admin apps. No other
  policies requested (no password forcing, no wipe)
- [x] P15.11: App shortcut provider — RoutineShortcutPublisher publishes top-4 routines as
  dynamic launcher shortcuts (PR #232)
- [x] P15.12: `open_url` tool — http/https allow-list, fast-path URL capture (PR #234)
- [x] P15.13: Permissions walkthrough — PermissionManager accepts multiple a11y / listener
  classes so either grant satisfies onboarding (PR #236)

## Phase 16 — Priority 2: Local LLM / offline completion
完全オフライン化。Wake / STT / LLM / TTS / tool execution 全てを on-device で完結。

- [ ] P16.1: whisper.cpp JNI — add `whisper.cpp` as submodule; CMake wire-up; WhisperSttProvider
  reads AudioRecord PCM, runs `whisper_full()`, emits `SttResult.Partial` + `Final`.
  Replace the existing OfflineSttStub. Ref: ggml-org/whisper.cpp
- [ ] P16.2: Silero VAD (ONNX) — `VadEngine` interface + SileroVadProvider via ONNX Runtime.
  Gates AudioRecord into speech/silence windows feeding WhisperSttProvider. Ref:
  sherpa-onnx silero-vad binding; snakers4/silero-vad
- [ ] P16.3: Piper TTS JNI — `piper-cpp` submodule + voice-model downloader; replace
  PiperTtsProvider fallback path with real inference. Ref: rhasspy/piper
- [ ] P16.4: Semantic memory embeddings upgrade — replace TF-IDF with MiniLM (all-MiniLM-L6-v2
  ONNX) for `semantic_memory_search`. Ref: shubham0204/Sentence-Embeddings-Android
- [ ] P16.5: Local knowledge base — bundled Wikipedia-lite (compressed) + SQLite FTS5 for
  `knowledge` tool offline fallback when no network
- [ ] P16.6: Model hot-swap — switch between Gemma / Qwen / Phi without relaunch;
  invalidate VoicePipeline engine reference, warm the new one on background thread
- [ ] P16.7: OpenClaw-level agent loop — parallel tool calls (multiple tool_calls per turn),
  tool result re-entry without reparsing the whole prompt, early stop on `answer:` marker.
  Ref: openclaw-assistant

## Phase 17 — Priority 4: Multi-room RPC protocol
複数スピーカーの連携。P14.5 で相互発見 + 配信登録まで済み、その上のプロトコル層。

- [x] P17.1: Wire format decision — ADR shipped at `docs/multi-room-protocol.md` (PR #239);
  JSON envelopes on TCP/8421, WebSocket primary with NDJSON fallback, HMAC-SHA256 auth,
  30-second replay window. Ref: OVOS message bus
- [x] P17.2: `AnnouncementServer` — **receiver done**, NDJSON path + RFC 6455 WebSocket
  upgrade (`GET /bus HTTP/1.1`, hand-rolled handshake / framing, no new deps).
  `HmacSigner` + `AnnouncementParser` + `AnnouncementDispatcher` +
  `AnnouncementServer` all shipped with unit tests. `MULTIROOM_SECRET` stored in
  SecurePreferences. VoiceService lifecycle starts/stops the server behind the existing
  `MULTIROOM_BROADCAST_ENABLED` toggle. `tts_broadcast` and `heartbeat` are wired; other
  message types parse cleanly but dispatch as `Unhandled`. **Client / sender** side is P17.3+
- [x] P17.3: **Sender side shipped** — `AnnouncementClient` (NDJSON) +
  `AnnouncementWebSocketClient` (OkHttp, WS-first with NDJSON fallback) +
  `AnnouncementBroadcaster` with fan-out across discovered peers; `BroadcastTtsToolExecutor`
  + `BroadcastTtsMatcher` provide the user-facing path. `broadcast_timer` envelope wiring
  for cross-speaker timer fan-out is a small follow-up on top of this (same broadcaster,
  different envelope type)
- [x] P17.4: Speaker groups — `SpeakerGroupEntity` + `SpeakerGroupRepository` persist client-side
  subsets; `AnnouncementBroadcaster.broadcastTtsToGroup` intersects with discovered peers;
  `BroadcastGroupMatcher` routes "broadcast X to kitchen" ahead of the unscoped matcher;
  `SettingsSpeakerGroupsScreen` manages add/remove/delete. Per ADR, groups stay client-side
  and never appear on the wire
- [x] P17.5: Session handoff — `AnnouncementType.SESSION_HANDOFF` envelope + dispatcher path
  seeds `ConversationHistoryManager` on receive (replace semantics — the user said "move
  this", not "also add this"); `HandoffMatcher` + `HandoffToolExecutor` on the send side.
  Media handoff stubbed as Unhandled with a TODO (requires active-MediaSession + position
  transfer, out of scope for P17.5)
- [x] P17.6: Pairing fingerprint — **shipped as word-phrase, not QR**. `PairingFingerprint`
  hashes the shared secret and maps the first 4 bytes against a bundled 256-word list;
  `SettingsMultiroomPairingCard` displays the 4-word phrase so users verify both speakers
  agree by reading to each other. Camera-based QR pairing intentionally deferred (camera dep,
  marginal UX gain over word-phrase). Full challenge-response handshake is a future-work
  item on the ADR backlog

## Won't do — requires root (design boundary)
- OEM modifications (CarrierConfig, RIL overrides)
- System-wide audio routing beyond MediaSession
- Low-level thermal/power-rail telemetry (we rely on PowerManager)
- Replacing the system launcher (users can pin us as default, but we don't require it)

---

## Legacy Phase 1-7 (kept for history)
All items below were done during the priority-agnostic phase. Keeping for reference.
<details>
<summary>Expand</summary>

- Phase 1: system prompt, conversation history, agent loop, chat templates, tool parsing
- Phase 2: timer, notifications, calendar, app launcher, volume, contacts
- Phase 3: weather, search, news, knowledge, web fetch, unit converter, calculator, currency
- Phase 4: compaction, user memory, device context, datetime
- Phase 5: multi-tool chaining, proactive, routines, screen reader, skills, location
- Phase 6: SMS, camera, screen record, photos, device health, routine persistence, semantic memory, RAG, vision, skill install
- Phase 7: tool analytics, permission catalog, suggestion state, repos for Settings UI

</details>

---

## Improvement Cycle Protocol

Each cycle:
0. **目的再確認**: 「Android タブレットをアレクサ相当 + OpenClaw 相当のローカルエージェントに」
1. Pick the next item **strictly in priority order** (P8 before P9 before P10 etc.)
2. Reference `/Users/y-c-hashimoto/Documents/GitHub/open-smart-speaker参考リポ/` when relevant
3. Create a feature branch (worktree)
4. Write tests first (TDD, 80%+ coverage)
5. Implement minimal code
6. `./gradlew testDebugUnitTest assembleDebug` must be green
7. PR with `## Priority` header citing which priority this addresses
8. Merge to main
9. Update this roadmap (check off, record learnings)
10. **整合性チェック**: 変更は priority top に近づいているか？Yes=続行 / No=巻き戻し
