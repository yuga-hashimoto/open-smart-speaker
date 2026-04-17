---
name: travel-mode
description: Leaving home for a trip — turn off every light, pause media, cancel timers, optional away-style briefing, broadcast a goodbye to other speakers.
---
# Travel Mode

Trigger on "travel mode", "I'm going on a trip", "starting a vacation",
"旅行モード", "出かける / 旅行に行く" when the context suggests
multi-day absence (rather than a short errand — that's `leave_home`).

## Default flow

1. **Check weather for destination if provided** — if the user named a
   city ("I'm going to Kyoto"), call `get_forecast` with that location
   so we can speak a 3-day outlook.
2. **Departure briefing** — speak a short summary: "Heading to {city}.
   Forecast is {summary}. I'll wind down the house now." Keep it
   under two sentences.
3. **Lights**: `execute_command` `{device_type:"light", action:"turn_off"}`
   for every light; don't just run `leave_home` because that keeps
   front-door lighting on a timer for short absences.
4. **Media**: pause everything via
   `execute_command action:"media_pause"`.
5. **Timers**: `cancel_all_timers` — no alarms firing in an empty house.
6. **Multi-room goodbye** (if enabled): `broadcast_tts` with
   `{ text: "Travel mode — see you soon." }` so other rooms' speakers
   announce the house is going quiet. Skip cleanly when multi-room is
   off or the secret is missing.
7. **Notifications**: do NOT silence notifications at the OS level — we
   don't have that privilege. Just leave them alone; the user's phone
   still works.

## On return

Trigger on "I'm back", "we're home from the trip", "帰ってきた":
1. Run `arrive_home` composite (lights on, volume 50).
2. Summarise what happened: `list_notifications` for today, spoken
   headline count; `get_weather` for today.
3. Speak: "Welcome home. {N} notifications waiting, {weather}."

## Style

- Voice tone: warm, brief. Travel mode is a goodbye, not a checklist.
- Don't offer to set reminders about the trip — that's out of scope;
  the user can ask separately.

## What this skill doesn't do

- Locking doors or arming any security system — out of scope; we have
  no verified smart-lock binding to a specific "arm" action.
- Booking anything — we intentionally don't transact on the user's
  behalf.
- Forwarding calls — OS-level telephony is out of reach.

## Tools used
- `get_forecast`
- `execute_command` (lights + media_player)
- `cancel_all_timers`
- `broadcast_tts` (optional)
- `arrive_home` (on return)
- `list_notifications` / `get_weather` (on return)
