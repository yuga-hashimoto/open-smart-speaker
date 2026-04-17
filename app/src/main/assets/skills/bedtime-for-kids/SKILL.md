---
name: bedtime-for-kids
description: Evening wind-down for a family — broadcast a gentle warning to every speaker, then dim lights, pause media, and set a short cool-down timer everywhere.
---
# Bedtime for Kids

Trigger on "bedtime for the kids", "tell the kids it's bedtime",
"子供たちに寝る時間", or similar whole-house evening wind-down cues.

## Default flow

1. **First warning** (15 min before): call `broadcast_announcement` with
   `{ text: "Fifteen minutes until bedtime", ttl_seconds: 900 }` so every
   speaker both speaks it and keeps a banner up while the kids brush
   teeth.
2. **Start the cool-down timer on every speaker**: `broadcast_timer`
   with `{ seconds: 900, label: "bedtime" }`. When any speaker's timer
   fires, that room's speaker will chime — consistent household cue.
3. When the user later says "OK bedtime now" / "もう寝る時間":
   - `broadcast_tts` "Lights out. Good night." to every speaker.
   - Locally run the existing `goodnight` composite tool (lights off,
     media pause, cancel timers) on this device.
   - Fan out to other speakers isn't possible without a future
     `run_routine_broadcast` tool — leave that as user education: "Say
     'goodnight' on each speaker, or use a smart-home group for the
     lights."

## Style

- Tone: calm, parent-voice. No over-explaining.
- If multi-room isn't set up, degrade to local-only: speak the warning,
  run `goodnight` here, say "tell the kids on their own speakers too".

## Tools used
- `broadcast_announcement`
- `broadcast_timer`
- `broadcast_tts`
- `goodnight` (local composite)
