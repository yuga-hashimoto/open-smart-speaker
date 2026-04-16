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
- [ ] P8.2: Wake-to-listening latency budget — target <500ms visual feedback after wake
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
- [ ] P10.4: Accessibility pass (TalkBack, large-text)
- [ ] P10.5: Dark/light mode consistency
- [ ] P10.6: Music Assistant / media control UI (inspired by dash-voice)

## Phase 11 — Priority 4: Hybrid / External Gateway
- [ ] P11.1: HermesAgent protocol adapter (new AssistantProvider)
- [ ] P11.2: OpenClawProvider streaming + tool forwarding
- [ ] P11.3: "Heavy task" hint — escalate to gateway when needed
- [ ] P11.4: Unified provider switcher polish

## Phase 12 — Priority 5: Refactor / Quality
- [ ] P12.1: Dead code sweep (VoicePipeline TTS/audio branches)
- [ ] P12.2: CameraX integration (replaces skeleton)
- [ ] P12.3: MediaProjection integration (replaces skeleton)
- [ ] P12.4: SecurePreferences audit — no plaintext
- [x] P12.5: Unified OkHttp with sensible timeouts — TtsManager no longer builds its own client; all HTTP uses NetworkModule singleton (30s connect / 60s read / 30s write)
- [ ] P12.6: Coverage report — aim 80% non-UI
- [ ] P12.7: Android Lint baseline / zero warnings in tool/

## Phase 13 — Priority 6: OSS Project Health
- [x] P13.1: CONTRIBUTING.md (priority-order guide, code style, tool/skill authoring)
- [x] P13.2: Issue + PR templates (bug report / feature request / PR template)
- [x] P13.3: CI workflow already exists (gradle test + lint)
- [x] P13.4: Release workflow — tag push builds debug APK and attaches to release
- [x] P13.5: SECURITY.md with threat model + responsible disclosure
- [x] P13.6: CODE_OF_CONDUCT.md (Contributor Covenant 2.1)
- [x] P13.7: README overhaul — value prop, hardware guide, tool list, architecture diagram, inspiration credits
- [ ] P13.8: Docs site covering architecture, SKILL.md authoring, tool list

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
