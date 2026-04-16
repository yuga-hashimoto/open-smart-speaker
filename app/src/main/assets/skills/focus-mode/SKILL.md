---
name: focus-mode
description: Deep-work session — quiet alerts, steady light, optional Pomodoro timer.
---
# Focus Mode

Trigger on "focus mode", "focus time", "deep work", "study session",
"start a pomodoro", "集中モード", "勉強モード", "ポモドーロ".

## Default flow
1. Set lights to a steady 70% via `execute_command` with
   `{device_type:"light", action:"set_brightness", parameters:{brightness:70}}`.
   Bright enough to read by, low enough to avoid eye strain.
2. Drop the speaker volume to 20 via `set_volume` so notifications and
   filler phrases stay quiet.
3. Call `clear_notifications` once so the first stretch is interrupt-free.
4. If the user asks for a Pomodoro: default to a 25-minute timer via
   `set_timer(seconds=1500)`. If they specify a duration ("45 minute
   focus"), honor it.
5. After the focus block ends and the user signals "break", suggest a
   short 5-minute timer.

## Style
- One short confirmation: "Focus on." or "Let's focus."
- No interruptions until the timer fires.
- When the timer fires, a single short prompt: "Focus block done. Take
  a break?"
- If the user says "focus over" / "stop focusing", restore lights to
  100% and volume to 50.
