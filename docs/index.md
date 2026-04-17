# OpenSmartSpeaker Docs

Tablet-first Android smart speaker with on-device LLM agent, inspired by Alexa and OpenClaw.

## Start here

- **[Architecture](architecture.md)** — layer map, key abstractions
- **[Conventions](conventions.md)** — Kotlin style, package layout, testing
- **[Roadmap](roadmap.md)** — priority-ordered backlog

## Reference

- **[Tools](tools.md)** — every LLM-callable tool and its parameters
- **[Fast paths](fast-paths.md)** — voice utterances handled without the LLM
- **[Providers](providers.md)** — AssistantProvider implementations
- **[Skills](skills.md)** — authoring `SKILL.md` files
- **[Permissions](permissions.md)** — runtime + special permissions
- **[Multi-room protocol](multi-room-protocol.md)** — WebSocket message bus design
- **[Real-device smoke test](real-device-smoke-test.md)** — on-device validation run before each release

## Running locally

```bash
./gradlew testDebugUnitTest   # unit tests (~500)
./gradlew lintDebug           # Android Lint (baseline applied)
./gradlew assembleDebug       # arm64-v8a APK
```

## Priority order

Every change must advance one of these:

1. **Smart home device feel** — Alexa-class response latency, voice-first
2. **Local agent capabilities** — OpenClaw-style tools / skills / memory on-device
3. **UX polish** — ambient, onboarding, error recovery
4. **Hybrid gateway** — escalate heavy tasks to OpenClaw / HermesAgent
5. **Refactor / quality** — code health, security, performance
6. **OSS project health** — docs, CI, community

Each PR's description must name which priority it advances.
