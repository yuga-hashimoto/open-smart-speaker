---
name: power-nap
description: 20-minute restorative nap — dim lights to 20%, set a short timer, whisper a goodnight, then a gentle wake cue.
---
# Power Nap

Trigger on "power nap", "quick nap", "20 minute nap", "twenty minute nap",
"パワーナップ", "昼寝", "短い昼寝". Distinct from `bedtime-routine` (full
overnight wind-down) and `meditation` (active, eyes-closed attention).
This is a recovery nap — short, shallow, with a clean wake cue.

## Default flow

1. **Check context first**: if `quiet-hours` is already active, skip the
   light-dim step — the room is already low. Don't dim further, don't
   brighten. Just go straight to the timer.
2. **Dim lights to 20%**: `execute_command` `{device_type:"light",
   action:"set_brightness", parameters:{brightness:20}}`. Warm, not
   black — napper needs enough signal to wake naturally near the end.
3. **Set the timer**: `set_timer` with `{ seconds: 1200, label:
   "power nap" }`. Twenty minutes is the sweet spot — long enough to
   refresh, short enough to avoid sleep-inertia from deep-sleep stages.
4. **Whisper the goodnight**: one short line at low volume.
   - EN: "I'll wake you in twenty."
   - JA: 「20分後に起こすね。」
5. Then silence. No follow-up chatter, no confirmation of what got
   dimmed, no proactive suggestions. Let them drop off.

## On timer fire

1. **Restore lights to 60%**: `execute_command` `{device_type:"light",
   action:"set_brightness", parameters:{brightness:60}}`. Not full
   brightness — we're coaxing, not startling.
   - If quiet-hours was active at start, restore only to the
     quiet-hours baseline (don't punch through it).
2. **Gentle wake voice** — warm, unhurried, medium volume:
   - EN: "Time to get up."
   - JA: 「起きる時間だよ。」
3. Pause two seconds, then stop. If the user doesn't respond within
   60 s, do nothing else — they may need another minute. Don't nag.

## Early cancel

User says "cancel nap", "wake me now", "起きた", "昼寝キャンセル":
1. `cancel_all_timers` scoped to the power-nap label.
2. Lights back to 60% (or quiet-hours baseline).
3. "Okay, you're up." / 「わかった、起きたね。」

## Style

- Soft register on the way down, warm register on the way up.
- No cheer, no "Rise and shine!" — this isn't morning, it's a reset.
- Do not broadcast. A nap is singular; other rooms don't need to know.

## Tools used
- `execute_command` (light set_brightness)
- `set_timer`
- `cancel_all_timers`

## Tools explicitly avoided
- `broadcast_tts` / `broadcast_announcement` — nap is private.
- `morning_briefing` / `evening_briefing` — post-nap brain doesn't want
  a news dump.
- `media_play` — no background audio; silence is the point.
