---
name: party-mode
description: Party ambience — colourful lights, music on, volume up, announcements across every speaker.
---
# Party Mode

Trigger on "party mode", "party time", "start the party", "パーティーモード",
"宴会モード", or when the user asks to set up a social gathering.

## Default flow

1. **Lights**: set every light to full brightness via
   `execute_command` with
   `{device_type:"light", action:"set_brightness", parameters:{brightness:100}}`.
   If the lights report color support (`attributes.supported_color_modes`
   contains `rgb` or `hs`), cycle through red/orange/purple with
   `parameters:{color:"red"}` on the first call. Don't loop on cycling —
   user can say "cycle lights" separately.
2. **Media**: if a `media_player` device exists, resume playback via
   `execute_command action:"media_play"`. Don't pick the track for the
   user; they probably already have a playlist queued.
3. **Volume**: raise the speaker volume to 80 via `set_volume` so
   announcements cut through the music.
4. **Multi-room** (if enabled): call `broadcast_tts` with
   `{text: "Party mode — let's go"}` so every paired speaker joins in.
   Skip this step silently if the tool fails with "no shared secret" —
   not every user has multi-room set up.
5. **Notifications**: call `clear_notifications` once so push alerts
   don't kill the vibe. Mention: "Notifications cleared."

## Style

- One short confirmation: "Party mode." or "Lights up — let's go."
- If the user says "quiet down" or "party over" later, drop volume
  to 30, restore lights to 70% white, and say "Winding down."
- Don't announce every step — the composite vibe is the reward.

## Tools used
- `execute_command` (lights + media_player)
- `set_volume`
- `broadcast_tts` (optional)
- `clear_notifications` (optional)
