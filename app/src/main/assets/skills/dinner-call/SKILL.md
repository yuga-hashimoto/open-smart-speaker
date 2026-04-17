---
name: dinner-call
description: Call the household to dinner — broadcast TTS to every speaker + pin a banner so latecomers see the message.
---
# Dinner Call

Trigger on "call everyone to dinner", "dinner's ready — tell the house",
"家族呼んで", "ご飯だよって伝えて", or any "bring everyone" phrase that
obviously wants a whole-house message.

## Default flow

1. **Broadcast TTS** via `broadcast_tts` with
   `{ text: "Dinner is ready", language: "en" }` (or `"ja"`). Every paired
   speaker speaks the message in the receiver's language if language is
   set.
2. **Pin a banner** via `broadcast_announcement` with
   `{ text: "Dinner is ready", ttl_seconds: 300 }`. The banner stays for
   5 minutes on every Ambient screen so a child walking into the kitchen
   after the initial call still sees it.
3. If multi-room is not set up (`broadcast_tts` returns "no shared secret"
   or "No peers found"), fall back to speaking locally and note out loud
   "Multi-room isn't set up yet — only I can call them."

## Style

- Keep the spoken version short — one phrase, no ritual preamble.
- Optional variation: if the user mentions a specific room ("upstairs"),
  switch to `broadcast_tts` with `group: "upstairs"` instead. Don't
  invent groups; rely on what the user has saved in Settings → Multi-room
  → Speaker groups.

## Tools used
- `broadcast_tts` (with optional `group`)
- `broadcast_announcement`
