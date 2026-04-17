---
name: leaving-home
description: Quick send-off when the user is leaving — lights out, low-battery reminder, optional weather preview, all inside a single calm sentence.
---
# Leaving Home

Trigger on "I'm leaving", "heading out", "going out", "see you later",
"行ってきます", "出かける". Complements `arrival-home` (return) and
`travel-mode` (multi-day trip): this is the 15-second send-off when
the user is walking out the door.

Keep it short — the user is on the way to the door with their phone
in one hand. Anything that makes them stop and reply is a failure.

## Default flow

1. **Lights off in common areas**: issue `execute_command` for any
   light with name containing "living", "kitchen", "bedroom",
   "リビング", "寝室", or that is currently `isOn == true`. Target
   `turn_off`. Skip any light with name containing "night", "常夜灯",
   or "porch" (users often leave those on by design).
2. **Quick weather preview** (optional, only if `get_weather`
   currently cached): speak the single-word condition ("Rain."
   / "Clear." / "雨です"). Omit the temperature — one syllable wins
   over three. If the weather cache is stale skip this step entirely
   rather than introduce a 2-second stall.
3. **Actionable reminder**: if any of the following is true, tack one
   short clause onto the send-off:
   - Tablet battery is below 20% and the app is running on-device:
     "Your tablet is low on battery."
   - `get_calendar_events { hours: 2 }` shows an event in the next
     two hours and the event has a location that is NOT "home" /
     "自宅": "You have X at HH:MM at LOCATION."
   - Otherwise, no extra clause.
4. **Send-off sentence**: wrap steps 2-3 with "Have a good one."
   / "いってらっしゃい". This is the final line — do not ask a
   question at the end.

## What this skill does NOT do

- No door-lock calls. That requires explicit opt-in
  (device_admin / HA lock entity) and is high-stakes; covered in
  `home-control` under the `lock_door` tool only.
- No navigation launch. Driving intents are a different flow.
- No multi-step dialog. If the user wants more they'll ask.

## Follow-ups

- If the user replies "weather" / "天気", fall back to
  `get_forecast` for the rest of today.
- If the user replies "lights on" / "つけて", revert step 1 on the
  specific light(s) they named.
