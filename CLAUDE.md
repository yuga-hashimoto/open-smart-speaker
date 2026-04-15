# OpenSmartSpeaker

Android smart speaker app. Kotlin 2.1.0 + Jetpack Compose + Material 3 + Hilt.

## Commands

```bash
./gradlew assembleDebug    # Build
./gradlew test             # Unit tests (JUnit 5 + MockK + Turbine + Truth)
./gradlew lint             # Android Lint
```

## Source Layout

All source under `app/src/main/java/com/opensmarthome/speaker/`:

- `assistant/` — AI provider abstraction + ConversationRouter
- `device/` — Smart home control (SwitchBot, Matter, MQTT, HA)
- `voice/` — VoicePipeline: wake word → STT → assistant → TTS
- `ui/` — Compose screens (Chat, Dashboard, Ambient, Settings, Home)
- `service/` — Foreground service for always-on voice
- `data/` — Room DB, encrypted preferences
- `tool/` — LLM function calling interface

Details: [docs/architecture.md](docs/architecture.md)

## Conventions

- Immutable data: `data class` + `copy()`, never mutate
- Coroutines for async, `Flow` for streams
- Null safety: no `!!`, use `?.let {}` or `?:`
- Sealed classes for state and result types
- Package-per-feature, file name = primary class name
- Test files: `{ClassName}Test.kt` in `app/src/test/`

Details: [docs/conventions.md](docs/conventions.md)

## Constraints

- **DO NOT** modify `app/src/main/cpp/llama.cpp/` (git submodule)
- **DO NOT** hardcode secrets — use `SecurePreferences`
- **DO NOT** add dependencies without asking the user
- **DO NOT** skip tests — always run `./gradlew test` after changes
- **DO NOT** write code that only targets one provider — respect the `AssistantProvider` / `DeviceProvider` abstractions

## Testing

- Coverage goal: 80%+
- Write test first (RED → GREEN → REFACTOR)
- After implementation: run `./gradlew test`, fix failures before declaring done
- HTTP tests: use `MockWebServer`

## Improvement Loop

Roadmap: [docs/roadmap.md](docs/roadmap.md)

Cycle: Plan → Branch → TDD → Implement → Verify → PR → Merge → Update roadmap

Agents:
- `improvement-planner` — picks next item, researches, produces plan
- `improvement-verifier` — build/test/lint/review after implementation
