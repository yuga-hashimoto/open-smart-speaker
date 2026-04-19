---
title: End-to-End Testing
---

# End-to-End Testing

OpenDash ships a layered test pyramid. This page explains the **E2E layer** —
what is automated, what is not, and how to run it locally / in CI.

## Pyramid

| Layer | Lives in | Tools | Automated? |
|---|---|---|---|
| **L1 Unit** | `app/src/test/` | JUnit 5, MockK, Turbine, MockWebServer | Fully — `./gradlew testDebugUnitTest` |
| **L2 Component (UI)** | `app/src/test/...ui/` (Robolectric where applicable) | Compose UI test | Fully — same Gradle task |
| **L3 E2E (instrumented)** | `app/src/androidTest/` | UiAutomator, Hilt-test, Compose UI test | Fully — needs an emulator or device |
| **L4 Smoke (real device)** | [docs/real-device-smoke-test.md](real-device-smoke-test.md) | Manual + adb scripts | Half — checklist-driven |

L1/L2 catch logic regressions; L3 catches Android runtime / DI / lifecycle
regressions; L4 catches everything that needs a real microphone, speaker,
LAN router, or thermal sensor (and therefore can't run in CI).

## Why E2E can't be 100 % automated

OpenDash is voice-first. The hardware loop —

```
microphone → wake word → STT → router → LLM → TTS → speaker
```

— has three pieces (mic, speaker, on-device model) that can't be reproduced
faithfully on a CI emulator:

- **Wake word**: `VoskWakeWordDetector` consumes raw 16 kHz PCM. Emulator
  audio inputs are silence by default; the only way to drive it is to feed
  PCM through `adb`, and even then the detector's confidence numbers don't
  match real-room acoustics.
- **TTS / barge-in**: TextToSpeech engines vary across Android versions and
  OEM ROMs. Asserting "TTS halted within 200 ms of wake" is meaningful only
  on the speaker users actually own.
- **mDNS multi-room**: requires multicast across at least two devices on the
  same LAN. Hosted CI runners block multicast.
- **Thermal / battery**: real silicon only.

So the strategy is **swap the hardware boundaries with fakes** for L3 and
keep L4 manual. Specifically:

- `SttProvider`, `TtsProvider`, `AssistantProvider`, and `WakeWordDetector`
  are already abstractions, so a future PR can swap them via Hilt
  `@TestInstallIn` without touching production code.
- `FastPathRouter`, `ToolExecutor`, `LatencyRecorder`, `ErrorClassifier`
  are pure Kotlin and are exercised today by L1 unit tests + a thin L3
  router smoke test.

## What's automated today

`app/src/androidTest/java/com/opendash/app/`:

| File | What it covers |
|---|---|
| `HiltTestRunner.kt` | Boots `HiltTestApplication` so `@HiltAndroidTest` works. |
| `e2e/AppLaunchE2ETest.kt` | Cold-launches `MainActivity` via UiAutomator, asserts a known landing-screen text appears. |
| `e2e/FastPathRouterE2ETest.kt` | Sanity-checks `DefaultFastPathRouter` matches `set_timer` (EN+JA), `help` (speak-only), and lets ambiguous queries fall through to the LLM. |
| `e2e/HiltInjectionE2ETest.kt` | Demonstrates the `@HiltAndroidTest` pipeline by round-tripping a value through the real `AppPreferences` `DataStore`. |
| `e2e/VoicePipelineFastPathE2ETest.kt` | Drives the **real** `VoicePipeline.processUserInput(text)` through FastPathRouter → ToolExecutor → TimerManager → TTS confirmation, asserting EN+JA `set_timer` utterances both speak the right confirmation and create the right alarm. Uses `fakes/FakeTextToSpeech` swapped via `FakeTtsTestModule`. |

### Swapping providers via `@TestInstallIn`

Each provider boundary that we want to swap lives in its own Hilt module so a `@TestInstallIn(replaces = [<Module>::class])` can stub it out without touching the rest of the graph. Today:

| Boundary | Production module | Test module |
|---|---|---|
| `TextToSpeech` | `di/TtsModule.kt` → `TtsManager` | `e2e/fakes/FakeTtsTestModule.kt` → `FakeTextToSpeech` |

Future swaps (planned: `SpeechToText`, `AssistantProvider`, `WakeWordDetector`) follow the same pattern — extract the binding into its own module under `di/`, then add a `@TestInstallIn` test module under `androidTest/.../e2e/fakes/`. Keep production modules narrow so swapping one doesn't drag others.

Together these prove the four pieces of L3 plumbing work:

1. Custom `AndroidJUnitRunner` swaps in `HiltTestApplication`.
2. `@HiltAndroidTest` resolves real `@Singleton`s.
3. Pure logic exposed via `androidTest` runs against the device's ART.
4. `MainActivity` cold-starts without the production DI graph crashing.

## How to run

### Local

```bash
# Start an emulator first (or plug in a tablet/phone with developer mode on).
./gradlew connectedStandardDebugAndroidTest
```

Outputs:

- HTML report: `app/build/reports/androidTests/connected/standardDebug/`
- XML / logcat: `app/build/outputs/androidTest-results/connected/standardDebug/`

To run a single class:

```bash
./gradlew connectedStandardDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.opendash.app.e2e.FastPathRouterE2ETest
```

### CI (planned)

Add a job to `.github/workflows/android.yml` that brings up an Android Virtual
Device (e.g. `reactivecircus/android-emulator-runner@v2`, API 34, x86_64,
`google_apis`) and runs `connectedStandardDebugAndroidTest`. Use the
`standard` flavor — `full` pulls in the VOICEVOX native AAR which we don't
need on CI hardware and will inflate the AVD boot time.

## Adding a new E2E test

1. Decide the layer:
   - "Pure Kotlin logic" — keep it as a unit test under `app/src/test/`.
   - "Touches Android APIs (Context, DataStore, Room, …) but no UI" — `@HiltAndroidTest` under `app/src/androidTest/.../e2e/`. Use `HiltInjectionE2ETest` as the template.
   - "User flow across screens" — UiAutomator under `app/src/androidTest/.../e2e/`. Use `AppLaunchE2ETest` as the template.
2. Name the file `<Subject>E2ETest.kt`.
3. Hold tests to a 10 s wall-clock budget on a warmed emulator. If you need
   longer, mark the suite with `@LargeTest` so it can be excluded from PR runs.
4. Tests **must not** depend on the on-device LLM model being downloaded.
   Either swap the embedded provider with a fake or assert on screens that
   render before model download (`ModelSetupScreen`).

## What's not automated (intentional)

These cases stay in [docs/real-device-smoke-test.md](real-device-smoke-test.md):

- Wake-to-listening latency budget on real silicon (the L1 budget guard
  via `LatencyRecorder` runs in unit tests with virtual time, but real
  audio-thread scheduling only happens on hardware).
- Barge-in interrupting TTS that's actively driving the speaker.
- mDNS discovery / multi-room broadcast across two physical devices.
- Camera / MediaProjection consent flows (system UI we don't own).
- Thermal throttling, battery drain.

## References

- [docs/real-device-smoke-test.md](real-device-smoke-test.md) — the manual
  L4 checklist this page complements.
- [docs/architecture.md](architecture.md) — provider abstractions that
  make L3 fakes possible.
