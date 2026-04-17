---
name: eye-break
description: 20-20-20 eye-strain reminder for screen-heavy work — short verbal cue every 20 minutes to look 20 feet away for 20 seconds.
---
# Eye Break

Trigger on "eye break", "20-20-20", "rest my eyes", "目を休めて",
"画面疲れ". Distinct from `stretch-break` (body movement),
`hydration-reminder` (water intake), and `focus-mode` (stay on task):
eye-break is a **passive recurring cue** that fires every 20 minutes
while the user is at a screen and does not interrupt work.

## Default flow on activation

1. **Brief confirmation**: speak "Eye-break timer on. I'll nudge you
   every 20 minutes." at the user's current volume.
2. **Set a recurring 20-minute backstop** via repeated `set_timer` with
   `{ seconds: 1200, label: "eye-break" }`. On each fire, re-schedule
   the next iteration unless the user has ended the session.
3. **Don't dim lights** — looking into a dim room defeats the point.
4. **Don't pause media** — this is passive.

## On each timer fire

Speak one calm sentence:
- "Look away for twenty seconds — something at least twenty feet off."
- JA: "目を少し休めてください。遠くを二十秒ほど見てみましょう。"

Then silence. Don't narrate during the 20 seconds — that defeats the
eye-rest. After the pause, re-set the next 20-minute timer.

## End-of-session

Trigger on "eye break off", "stop reminding", "目休めオフ":
1. `cancel_all_timers` scoped to eye-break labels only. Do NOT cancel
   every timer — cooking / workout timers must survive.
2. Speak briefly: "Eye-break off."

## Quiet-hours interaction

If `quiet-hours` is active: **suppress the reminder entirely**. Don't
whisper, don't re-schedule — wait until quiet-hours ends, then resume
on the next 20-minute boundary. This skill is background polish; it
must never wake a sleeping household.

## Multi-room

Do NOT broadcast. Eye-break is personal. If the user has multiple
tablets running, each one tracks its own session.

## Style

- Tone: gentle reminder, never urgent.
- JA: polite-casual ("〜してください").
- Keep cues under 10 words — long reminders defeat the rest.

## Tools used
- `set_timer`
- `cancel_all_timers`
