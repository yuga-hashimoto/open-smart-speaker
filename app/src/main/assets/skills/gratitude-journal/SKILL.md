---
name: gratitude-journal
description: Nightly 3-item gratitude prompt — ask for three good things from today, store each via `remember` under the `gratitude` namespace, close with a gentle good night.
---
# Gratitude Journal

Trigger on "gratitude journal", "three good things", "感謝日記",
"今日良かったこと". A short bedtime practice: three items, one line
each, stored for later recall. Distinct from `bedtime-routine` (which
shuts the house down) and from `task-manager` (which is forward-looking)
— this is a reflective close to the day.

## Default flow

1. **Tone check**: keep the volume at whisper level. If quiet-hours is
   active, stay there; if not, it's still a wind-down ritual — speak
   softly. Do not raise volume for emphasis.
2. **Invite**: "Three good things from today. Take your time — what's
   the first?" Wait for the user. Don't rush or fill silence.
3. **Capture item 1**: when the user answers, call
   `remember` with:
   - `namespace`: `"gratitude"`
   - `key`: `"<YYYY-MM-DD>-1"` (today's date, item index)
   - `value`: the user's exact phrasing, trimmed.
   Acknowledge with one word: "Noted." or "うん。"
4. **Capture item 2**: "And the second?" → same `remember` call with
   key `"<YYYY-MM-DD>-2"`.
5. **Capture item 3**: "Last one." → `remember` with key
   `"<YYYY-MM-DD>-3"`.
6. **Close**: "That's three. Good night." — nothing more. Don't list
   them back; the list is for the user's private memory, not a
   performance.

## Short-circuit

If the user pre-lists all three in one utterance ("today I was
grateful for A, B, and C"), parse them in order and make three
`remember` calls in sequence before closing. Still respond with a
single "Noted. Good night."

## Recall variant

If the user asks "what was I grateful for yesterday?" / "先週の感謝
日記見せて", call `recall` with `namespace:"gratitude"` and filter by
date prefix. Read entries back quietly, one per line. Do not
editorialize.

## Style

- Quiet, unhurried. This is the last thing the user hears before
  sleep.
- No "Great!" / "That's wonderful!" — keep it flat and respectful.
- Single-line acknowledgements only.

## Tools used
- `remember` (namespace: `gratitude`, key: date-indexed)
- `recall` (optional, for lookback variant)

## Tools explicitly avoided
- `broadcast_tts` / `broadcast_announcement` — gratitude is personal,
  never shared to other rooms.
- `set_volume` up — never raise; only ever whisper.
- `morning_briefing` / `evening_briefing` — this skill is the briefing,
  stripped down.
