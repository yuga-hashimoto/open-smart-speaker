---
name: quiet-hours
description: Night-time noise cap — low TTS volume, skip broadcast fan-outs, pause alerts until morning.
---
# Quiet Hours

Trigger on "quiet hours", "sleep-friendly mode", "静かにして" at night,
or any phrase that suggests the household is asleep / has a sleeping
infant.

Distinct from `goodnight` (one-time bedtime routine) and
`bedtime-routine` (wind-down): quiet-hours is a **stateful mode** that
persists until the user says "quiet hours off".

## Default flow on activation

1. **Volume**: `set_volume` to 10. Any TTS from here stays whisper-low.
2. **Announce to self**: speak a quiet "Quiet hours on. I'll keep it
   down." — confirmation at the new volume is itself a test.
3. **No multi-room fan-out** — do NOT call `broadcast_tts` /
   `broadcast_timer` / `broadcast_announcement` while quiet-hours is
   active. Other speakers might be in rooms where the baby is sleeping.
   If the user explicitly asks for a broadcast, confirm: "Quiet hours
   is on — broadcast anyway?"
4. **Skip proactive suggestions** — don't spontaneously offer morning
   briefings / reminders.
5. **Lights**: don't force lights off — the user may have already set
   the scene. Only dim brighter-than-40 % lights to 40 %.

## While quiet-hours is active

- All responses keep TTS short. One sentence max.
- Timers still fire at their original volume (system audio, not TTS —
  we can't silence the Android alarm channel without the user's Do Not
  Disturb settings).
- If the user asks the agent something ambiguous, prefer a fast-path
  answer over an LLM round-trip to save the fan noise from model
  inference.

## End-of-session

Trigger on "quiet hours off", "morning now", "静かモード終わり":
1. Restore volume to 50.
2. Speak: "Good morning."
3. If multi-room is on, **do not** fan out "good morning" — let each
   speaker greet when its own human wakes up.

## Style

- Tone: gentle, not cheerful. This is the middle-of-the-night mode.
- Japanese: always formal-polite. Matches night-time register.

## Tools used
- `set_volume`
- `execute_command` (lights — only for dimming)

## Tools explicitly avoided while active
- `broadcast_tts`, `broadcast_timer`, `broadcast_announcement`,
  `broadcast_cancel_timer`, `handoff_session`
- `play_morning_briefing`, `morning_briefing` (composite)
