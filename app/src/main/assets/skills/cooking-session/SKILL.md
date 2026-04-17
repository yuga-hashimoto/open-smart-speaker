---
name: cooking-session
description: Multi-timer cooking helper — parallel timers with labels, optional whole-house "dinner is ready" broadcast at the end.
---
# Cooking Session

Trigger on "cooking mode", "I'm starting dinner", "料理モード", "晩ご飯
の準備", or when the user asks for multiple concurrent kitchen timers.

Distinct from a single `set_timer`: cooking-session expects 2-5 parallel
timers (rice, pasta, sauce, oven) with labels, and offers an end-of-
cook "dinner is ready" broadcast to the household.

## Default flow

1. **Acknowledge briefly**: "Cooking mode. What needs timing?"
   Don't pre-set anything.
2. **Collect timers**: the user lists them — "rice 15, pasta 11, chicken
   22". For each, call `set_timer` with
   `{ seconds: <N * 60>, label: "<food>" }`. Labels are important; when
   a timer fires the system speaks the label, so the cook knows what
   finished.
3. **Optional kitchen music**: if the user asks, call `launch_app` with
   a music app name or `execute_command action:"media_play"` on a
   `media_player` if one is registered. Default to silence — cooking
   requires focus.
4. **Adjust on the fly**: "Change the chicken to 25 minutes" → call
   `cancel_timer` with the matching id (if known) or `cancel_all_timers`
   then re-set. The agent should grep the returned timer list for the
   label when the user refers to one by name.
5. **Recipe lookup**: if the user asks "how long for medium-rare
   salmon?" or "convert 1 cup flour to grams", call `web_search` or
   `convert_units`. Keep the answer to one sentence.

## End-of-cook

When the user says "dinner's ready" / "ご飯できた":
1. `cancel_all_timers` so nothing chimes mid-meal.
2. If multi-room is on: `broadcast_tts` with
   `{ text: "Dinner is ready", language: <detected> }` so every room's
   speaker calls the family.
3. `broadcast_announcement` with `{ text: "Dinner is ready",
   ttl_seconds: 600 }` so a late-arriving teenager still sees the
   banner on the ambient screen.
4. Speak local confirmation: "Told the house."

## Style

- Short prompts, no recipe preamble.
- Japanese: mirror the user's choice of formal / casual speech
  ("です・ます" vs "だ・である") — cooking is casual for most users.

## Tools used
- `set_timer`, `cancel_timer`, `cancel_all_timers`, `list_timers`
- `broadcast_tts`, `broadcast_announcement` (optional, multi-room)
- `execute_command` (media_player, optional music)
- `launch_app` (optional)
- `web_search` / `convert_units` (optional, recipe lookup)
