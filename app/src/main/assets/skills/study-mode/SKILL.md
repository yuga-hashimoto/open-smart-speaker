---
name: study-mode
description: Quiet study session — dim lights, pause music, silence notifications, optional Pomodoro timer, and kill distracting apps.
---
# Study Mode

Trigger on "study mode", "let's study", "start studying", "勉強モード",
"集中したい", or similar focus cues targeted at schoolwork or exams.

Distinct from `focus-mode` (which is general deep-work): study-mode leans
heavier on silencing distractions and optionally setting a Pomodoro-style
timer. Pick whichever matches the user's phrasing — don't wildcard both.

## Default flow

1. **Lights**: set every light to ~60 % white via `execute_command`
   `{device_type:"light", action:"set_brightness", parameters:{brightness:60}}`.
   Not full — full brightness is fatiguing for long study sessions.
2. **Sound**: call `set_volume` with `{level: 15}` so the speaker doesn't
   blare if it speaks again.
3. **Pause media** if any media_player is playing — use `execute_command`
   `{device_type:"media_player", action:"media_pause"}`.
4. **Clear notifications** via `clear_notifications`. Mention it: "Cleared
   notifications."
5. **Pomodoro timer** — if the user said "25 minutes" / "1時間" / "Pomodoro",
   call `set_timer` with the requested seconds. Default to 1500 (25 min) if
   no length specified. Confirm: "Study timer: 25 minutes."
6. **App hygiene** — DO NOT force-kill apps (we don't have that privilege
   without root). Instead, nudge: "I'd also pause social apps. Want me to
   open Settings → Digital Wellbeing?" If they say yes, call
   `open_settings_page` with `{page: "apps"}`.

## End-of-session

Trigger on "study mode over", "I'm done studying", "勉強終わり":
1. Restore lights to 80 % white.
2. Restore volume to 50.
3. Speak: "Nice work. Break time."

## Style

- Tone: calm, encouraging, no cheerleader-ing.
- If the user sets a timer, quote the length back before the flow starts.
- Silence is a feature — don't over-narrate; let the ambient state speak.

## Tools used
- `execute_command` (light + media_player)
- `set_volume`
- `clear_notifications`
- `set_timer`
- `open_settings_page` (optional, user-consented)
