---
name: wake-up-gently
description: Progressive sunrise-style wake — lights ramp up over 10 minutes while volume stays quiet, then gentle music + briefing.
---
# Wake Up Gently

Trigger on "wake me up at 7 gently", "sunrise alarm", "ゆっくり起こして",
"朝7時にゆっくり起こして", or any "wake me up" phrasing that mentions
gentle / sunrise / progressive.

Distinct from `AlarmMatcher` / `set_timer`, which fire a single chime
at the target time: wake-up-gently **ramps** across ~10 minutes
starting ~10 minutes before the target.

## Default flow

1. **Schedule the ramp**: the user says "7 am", so:
   - 6:50 → lights on at 10 %, warm colour temp
   - 6:55 → lights at 40 %
   - 7:00 → lights at 80 %, light music / nature sounds at volume 15
   - 7:05 → spoken greeting + short morning briefing at volume 35

   Use `set_timer` with a label per step, or (preferred) install the
   sequence as a routine the user can re-use.
2. **Avoid jarring transitions**: never jump brightness by more than
   30 % in one step. Never start TTS at volume > 20 until the lights
   have been up for at least 3 minutes.
3. **Greeting**: at ramp-end, speak a short personal line. If the
   user has a known morning routine, offer to run it: "Morning. Want
   your morning briefing?"
4. **Multi-room coupling**: do NOT wake other speakers unless the
   user explicitly said "for everyone". A kid down the hall doesn't
   need your 7 am ramp.

## On manual dismiss

If the user speaks to the agent during the ramp ("I'm up" / "もう起きた"):
1. Cancel any remaining scheduled steps (`cancel_all_timers` scoped to
   this wake sequence — if the agent knows the ids).
2. Jump lights straight to 80 %.
3. Speak: "Okay, good morning."

## Style

- Keep every spoken confirmation under 8 words.
- JA: gentle register ("おはようございます" — NOT "おはよー").

## Tools used
- `execute_command` (light, with brightness + color_temp parameters)
- `set_timer` (for the ramp steps)
- `set_volume`
- `morning_briefing` (optional, at ramp-end)
- `cancel_all_timers` (on dismiss)

## Tools explicitly avoided
- `broadcast_tts` / `broadcast_timer` — wake-up is personal; don't
  impose it on other rooms unless the user asked.
