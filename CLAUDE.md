# OpenSmartSpeaker

Android tablet smart speaker with on-device LLM agent.
Kotlin 2.1 + Jetpack Compose + Material 3 + Hilt + LiteRT-LM.

## Vision
Alexa-class smart-home device **+** OpenClaw-class local AI agent, on a single Android tablet.

## Priority Order (strict)
1. **Smart home device feel** — Alexa-class immediate response, voice-first
2. **Local agent capabilities** — OpenClaw-style tools, skills, memory on-device
3. **UX polish** — ambient, onboarding, error recovery
4. **Hybrid gateway** — escalate heavy tasks to external OpenClaw / Hermes
5. **Refactor / quality** — code health, performance, security
6. **OSS project health** — docs, CI, community

Every PR must name which priority it advances. See [docs/roadmap.md](docs/roadmap.md).

## Commands
```bash
./gradlew testDebugUnitTest    # unit tests (400+)
./gradlew assembleDebug        # build APK
./gradlew lint                 # Android Lint
```

## Source Layout
All under `app/src/main/java/com/opensmarthome/speaker/`:
- `assistant/` — AssistantProvider + router + skills + routines + agent planning + proactive suggestions + context
- `device/` — Smart home providers (HA, SwitchBot, MQTT, Matter) + DeviceManager
- `voice/` — VoicePipeline + FastPathRouter + LatencyRecorder + ErrorClassifier + STT/TTS
- `tool/` — 50+ LLM tools (system/info/memory/rag/accessibility/analytics)
- `ui/` — Compose screens (Chat, Dashboard, Ambient, Settings, Home) + VoiceOrb
- `permission/` — PermissionCatalog + PermissionRepository + Intents
- `service/` — Foreground + VoiceInteraction + Boot receivers
- `data/` — Room DB + encrypted preferences

Details: [docs/architecture.md](docs/architecture.md)

## Reference Repos (steal from these)
Located at `/Users/y-c-hashimoto/Documents/GitHub/open-smart-speaker参考リポ/`:
- **Ava** — voice state machine, wake ripple, proximity sensor
- **openclaw-assistant / OpenClawAssistant** — VoiceInteractionService, mic orb
- **off-grid-mobile-ai** — hardware-aware LLM init, context compaction, multi-format tool parsing
- **dash-voice** — tablet smart-home UX
- **local-llms-on-android** — LiteRT-LM integration
- **ViewAssist** — ambient info panel
- **openclaw** (official, TS) — tool list reference, skills system, memory system

Every PR that steals from one of these should say "stolen from X" in the commit message.

## Conventions
- Immutable data: `data class` + `copy()`, never mutate
- Coroutines for async, `Flow` for streams
- Null safety: no `!!`, use `?.let {}`, `?:`, or `takeIf { }?.let`
- Sealed classes for state and result types
- Package-per-feature, filename = primary class name
- Test files: `{ClassName}Test.kt` in `app/src/test/`

Details: [docs/conventions.md](docs/conventions.md)

## Constraints
- **DO NOT** modify `app/src/main/cpp/llama.cpp/` (git submodule)
- **DO NOT** hardcode secrets — use `SecurePreferences` (EncryptedSharedPreferences)
- **DO NOT** add dependencies without asking the user
- **DO NOT** skip tests — always run `./gradlew test` after changes
- **DO NOT** write code targeting one provider only — respect `AssistantProvider` / `DeviceProvider` abstractions
- **DO NOT** add external API services without mentioning it — CLAUDE global rule: "External Service Review" required

## Testing
- Coverage goal: 80%+ for non-UI code
- Write test first (RED → GREEN → REFACTOR)
- After implementation: `./gradlew test`, fix failures before declaring done
- HTTP tests: use `MockWebServer`
- Coroutine tests: `StandardTestDispatcher` + `runCurrent` / `advanceTimeBy`

## Improvement Loop
Cycle (repeat until user says stop):
1. **Re-read priority order** above
2. Pick next unchecked item from `docs/roadmap.md`, respecting priority order
3. Worktree-based feature branch
4. TDD — write tests first
5. Implement minimal code
6. `./gradlew testDebugUnitTest assembleDebug` must be green
7. Commit with `## Priority N` header
8. Push + PR + merge
9. Update `docs/roadmap.md`
10. Continue next cycle without asking the user

Agents:
- `improvement-planner` — picks next item, researches, produces plan
- `improvement-verifier` — build/test/lint/review after implementation
