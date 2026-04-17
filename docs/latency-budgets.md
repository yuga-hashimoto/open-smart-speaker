# Latency budgets & Span metrics

Priority 1 ("Alexa-class immediate response") is only meaningful if we
measure it. `LatencyRecorder` tracks per-span timing for the voice
pipeline so we know when we've regressed below the bar a smart
speaker is expected to clear.

## Budgets

| Span                     | Budget | What it measures                                        |
| ------------------------ | ------ | ------------------------------------------------------- |
| `WAKE_TO_LISTENING`      | 500 ms | Wake word detected → visual "listening" feedback shown. |
| `FAST_PATH_TO_RESPONSE`  | 200 ms | Fast-path utterance → first audible / visible response. |
| `TTS_PREPARATION`        | 400 ms | "Preparing speech" → first audio frame emitted.         |
| `STT_DURATION`           | 5 s    | User speech start → STT final transcript. Soft ceiling. |
| `TOOL_EXECUTION`         | 2 s    | Single tool call enter → exit.                          |
| `LLM_ROUND_TRIP`         | 8 s    | Prompt sent → final reply token.                        |

Budgets live on the `Span` enum in
[`LatencyRecorder.kt`](../app/src/main/java/com/opensmarthome/speaker/voice/metrics/LatencyRecorder.kt).
Raising a budget is a Priority-1 regression and requires a PR to
justify it.

## Why these numbers

- **500 ms wake-to-listening** is the threshold beyond which users
  perceive a gap. Alexa / Nest Hub consistently land under it.
- **200 ms fast-path-to-response** is the "instant" bar: perceived as
  no delay. Our timers, volume, lights, time/date fast paths all fit.
- **400 ms TTS preparation** is the longest silence a user tolerates
  before the assistant "sounds broken". Falls back to a filler phrase
  (`FillerPhrases`) if exceeded.
- **5 s STT duration** is a generous sanity ceiling — real utterances
  are 1-3 s. Anything longer likely means a stuck mic or a lost VAD
  endpoint.
- **2 s tool execution** is the soft ceiling before we surface a
  filler ("checking…"). Most tools come in well under this.
- **8 s LLM round-trip** — the tablet is running a ~1 B parameter LLM
  on CPU. Warm-start prompts fit easily; cold starts creep toward the
  cap.

## How to instrument a new code path

```kotlin
// At the enter point:
recorder.startSpan(Span.TOOL_EXECUTION, key = toolCall.id)

// At the exit point (may be in a different scope):
recorder.endSpan(Span.TOOL_EXECUTION, key = toolCall.id)
    ?.also { ms -> Timber.d("Tool $toolCall took ${ms}ms") }
```

Rules:
- Always pass a unique `key` when multiple spans of the same type may
  be in flight (e.g. per-`toolCall.id`). Otherwise the default `key`
  = span name is fine.
- Do NOT wrap the entire `coroutineScope { }` body — wrap the narrow
  window you are measuring. Spans cannot be nested on the same key.
- If the operation can fail, endSpan in the `finally` block.

## Watching the numbers

- **In-app**: Settings → Analytics → Latency card shows live budget
  violations via `LatencyRecorder.budgetViolations()`.
- **Runtime**: Every endSpan that blows its budget emits a Timber
  warning (`Latency budget exceeded: WAKE_TO_LISTENING took 680ms`).
  `adb logcat -s OpenSmartSpeaker` surfaces these.
- **CI**: No CI assertion yet; instrumented-test enforcement is on
  the Phase 16 backlog.

## Real-device smoke test

`docs/real-device-smoke-test.md` lists scripted runs where the budgets
matter. Step 2 ("wake latency") is the canonical measurement — cold
start, 10 wakes, report worst-case.

## Related

- [`LatencyRecorder.kt`](../app/src/main/java/com/opensmarthome/speaker/voice/metrics/LatencyRecorder.kt)
- [`VoicePipeline.kt`](../app/src/main/java/com/opensmarthome/speaker/voice/pipeline/VoicePipeline.kt) — primary caller.
- [real-device-smoke-test.md](real-device-smoke-test.md) — measurement playbook.
- [home-dashboard.md](home-dashboard.md) — a different kind of budget (refresh cadences, not latency).
