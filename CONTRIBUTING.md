# Contributing to OpenDash

Thank you for your interest! OpenDash aims to be the best open-source
Android tablet smart speaker — on par with Alexa for everyday use, and with
OpenClaw-level agent autonomy for power users.

## Quick Start

1. Fork the repo and clone your fork
2. Install Android Studio + Android SDK (API 35)
3. `./gradlew testDebugUnitTest` — all tests must pass before you push
4. `./gradlew assembleDebug` — must build
5. Make your change, add tests (TDD preferred), re-run the above
6. Open a PR against `main`

## Project Priority Order

Every change should advance one of these, in order:

1. **Smart home device feel** — it has to feel like Alexa for everyday use
2. **Local agent capabilities** — OpenClaw-style, on-device
3. **UX polish** — ambient, onboarding, error recovery
4. **Hybrid gateway** — connect to external OpenClaw / Hermes when needed
5. **Refactor / quality** — code, performance, security
6. **OSS project health** — docs, CI, community

See [docs/roadmap.md](docs/roadmap.md) for the full list.

## Code Guidelines

- **Kotlin** everywhere. No Java in new code.
- **Jetpack Compose + Material 3** for UI.
- **Hilt** for DI.
- **Room** for persistence.
- **Immutable data**: `data class` + `copy()`, no mutation.
- **Coroutines + Flow**, not callbacks.
- **No `!!`** — use `?.let`, `?:`, or throw explicit exceptions.
- **Sealed classes** for states and result types.
- One class per file, filename = primary class name.

## Adding a New LLM Tool

1. Create an interface + Android implementation if it needs system APIs
2. Wrap as a `ToolExecutor` returning `List<ToolSchema>` + `execute(ToolCall)`
3. Wire into `DeviceModule.provideToolExecutor`
4. Add a unit test covering happy path + error paths
5. Update CLAUDE.md / docs if the tool needs permissions or setup

## Adding a New Skill

Skills are `SKILL.md` files under `app/src/main/assets/skills/<name>/`:

```markdown
---
name: my-skill
description: One-line summary — this gets shown to the LLM.
---
# My Skill
Instructions the LLM loads on demand via `get_skill`.
```

Users can also install skills at runtime via the `install_skill_from_url` tool.

## Testing Requirements

- 80% coverage for non-UI code
- JUnit 5 + MockK + Truth for unit tests
- MockWebServer for HTTP
- Use `StandardTestDispatcher` for coroutine tests

## Commit & PR Style

- Small, focused commits. One PR = one concern when possible.
- Commit message: `<type>: <description>` where type is
  `feat | fix | refactor | docs | test | chore | perf | ci`
- PR description must cite which **Priority** (1-6) the change advances.
- No unreviewed merge to `main`.

## Security

Found a vulnerability? Please do NOT open a public issue.
See [SECURITY.md](SECURITY.md).

## License

By contributing you agree to license your contribution under the same license
as the project (see [LICENSE](LICENSE) if present, otherwise MIT).
