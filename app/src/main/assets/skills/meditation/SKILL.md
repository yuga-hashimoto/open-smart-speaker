---
name: meditation
description: Quiet meditation or breath-work session — very dim warm light, no media, silenced notifications, optional bell-ended timer.
---
# Meditation

Trigger on "meditation", "let's meditate", "breathing exercise",
"瞑想モード", "呼吸のセッション". Related to `reading-time` (quiet) and
`study-mode` (focus) but stricter about silence — NOTHING should speak
during a meditation unless the user asks.

## Default flow

1. **Lights**: `execute_command` `{device_type:"light",
   action:"set_brightness", parameters:{brightness:15}}`. If `color_temp`
   is available drop to ~2400K (very warm). Room stays visible but
   invitation to close eyes.
2. **Silence media**: `execute_command action:"media_pause"` on any
   active `media_player`.
3. **Silence notifications**: `clear_notifications` once.
4. **Volume**: drop to 5 — if the agent speaks at end-of-session, it's
   barely audible rather than jolting.
5. **Ask for duration**: if the user didn't specify, ask once: "How
   long? Five, ten, twenty minutes?" Default to 10 min if they say
   "whatever" or don't answer within 5 s.
6. **Start timer**: `set_timer` with `{ seconds: <N * 60>,
   label: "meditation" }`.
7. **Confirm minimally**: "Ten minutes, starting now." Then silence
   until the timer fires. **Do not** offer guided scripts (if user
   wants that, they'll say "guide me").

## On timer fire

The system plays the default alarm chime. Then the agent says, at
volume 5: "Session complete." Nothing else. Let the user decide to
come back to the world at their own pace.

## Guided variant

If the user says "guide me" or "読みながら":
- Between steps 4 and 5, call `retrieve_document` over any RAG-ingested
  meditation scripts. Read one segment, pause 30 s with `set_timer`,
  then next. Don't invent guidance — only read what's in the
  document store.

## End

User says "end meditation", "done", "終わり":
1. `cancel_all_timers` scoped to this session.
2. Lights back to 50 % warm.
3. Volume back to 30 (not 50 — keep the post-meditation register
   calm).
4. Speak softly: "Welcome back."

## Style

- Monk-voice. Quiet, short. Never cheerful.
- No "Good job!" — meditation is not a performance.

## Tools used
- `execute_command` (light + media_player)
- `clear_notifications`
- `set_volume`
- `set_timer`
- `cancel_all_timers`
- `retrieve_document` (optional, guided variant)

## Tools explicitly avoided
- `broadcast_tts` / `broadcast_timer` / `broadcast_announcement` —
  meditation is singular.
- `morning_briefing` / `evening_briefing` — noise.
