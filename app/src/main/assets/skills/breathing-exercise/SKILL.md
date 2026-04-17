---
name: breathing-exercise
description: Short 4-7-8 breathing guide — four cycles of inhale-hold-exhale voice cues over roughly one minute, without disturbing quiet-hours.
---
# Breathing Exercise

Trigger on "breathing exercise", "box breathing", "4 7 8 breathing",
"深呼吸", "呼吸法". Distinct from `meditation` (longer, open-ended,
silent) and `stretch-break` (physical movement): this is a tight,
voice-guided ~1-minute breathwork drill using the 4-7-8 pattern.

Each cycle: **inhale 4 seconds → hold 7 seconds → exhale 8 seconds**.
Run **4 cycles** (~76 seconds of breathing plus short intro/outro).

## Default flow

1. **Volume check**: if user is in quiet-hours (see `quiet-hours`
   skill), keep `set_volume` at 20 or below. Otherwise set to 30 —
   audible but unobtrusive.
2. **Intro**: speak one sentence: "Four cycles of four-seven-eight
   breathing. Follow my cues." Then pause 2 s before starting.
3. **Cycle loop** (repeat 4 times):
   - Say "Breathe in" at the start of inhale.
   - `set_timer { seconds: 4, label: "breath-inhale" }` — when it
     fires, say "Hold".
   - `set_timer { seconds: 7, label: "breath-hold" }` — when it
     fires, say "Breathe out slowly".
   - `set_timer { seconds: 8, label: "breath-exhale" }` — when it
     fires, pause ~500 ms before next cycle.
   - Between cycles, add a one-beat "Again." cue so the user knows the
     next inhale is starting.
4. **Outro**: after the 4th exhale timer fires, say once: "Done. Nice
   work." Do not congratulate beyond that — keep it calm.

## Variants

- **Box breathing (4-4-4-4)**: if the user says "box breathing"
  explicitly, use 4-second inhale, 4-second hold, 4-second exhale,
  4-second hold. Run 4 cycles (~64 s total). Add a "Hold empty" cue
  for the post-exhale hold.
- **Longer session**: if user says "keep going" / "もう一回" during
  outro, run another 4 cycles without re-intro.

## Interrupt handling

If the user says "stop" / "やめる" / "もういい" mid-session:
1. `cancel_all_timers` scoped to the breathing-exercise labels.
2. Speak once: "Okay." — no judgement, no recap.

## Quiet-hours respect

If `quiet-hours` is active:
- Never rise above volume 20.
- Skip the intro; just whisper "In", "Hold", "Out" instead of full
  sentences.
- No outro — end in silence.

## Multi-room

Do **NOT** `broadcast_tts` or `broadcast_announcement`. Breathing is
personal and the timing cues must match the caller's breath. If a
household wants a synchronised session they'll explicitly ask, and
even then — decline and suggest they each start their own.

## Style

- Monk-voice adjacent, like `meditation`: quiet, short, steady.
- Never cheerful. Never count aloud ("one… two…") — the timers do
  that work. Cue words only.
- JA: polite-casual, breath-length ("吸って", "止めて", "吐いて").

## Tools used
- `set_timer` (inhale / hold / exhale, short labels)
- `cancel_all_timers` (on interrupt or at end)
- `set_volume`

## Tools explicitly avoided
- `broadcast_tts` / `broadcast_timer` / `broadcast_announcement` —
  breath cues are individual and must not leak to other rooms.
- `execute_command` for lights — the user's eyes are likely closed;
  don't fiddle with the room state for a 1-minute drill.
- `morning_briefing` / `evening_briefing` — noise.
