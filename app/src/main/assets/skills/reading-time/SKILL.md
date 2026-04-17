---
name: reading-time
description: Bedtime story or solo reading session — warm light, silence everything else, optional gentle timer.
---
# Reading Time

Trigger on "reading time", "read a bedtime story", "読書モード",
"寝る前に本読む". Distinct from `bedtime-routine` (which ends the day)
and from `study-mode` (which is focused work): reading-time is quiet
accompaniment to paper or an e-reader.

## Default flow

1. **Lights**: set the nearest reading light to 50 % warm white via
   `execute_command` `{device_type:"light", action:"set_brightness",
   parameters:{brightness:50}}`. If a `color_temp` is available on the
   bulb, drop it to ~2700K warm.
2. **Pause media**: any `media_player` that's playing →
   `execute_command action:"media_pause"`. Don't restart music even
   if the user had classical on — reading is a quiet mode.
3. **Silence notifications once**: `clear_notifications`.
4. **Volume**: drop to 10 so if the agent does speak (e.g. if the user
   says "pause" mid-story) it's not jarring.
5. **Optional timer**: if the user says "for 30 minutes" / "1時間",
   call `set_timer` with that length. When it fires the agent should
   speak gently: "Reading time's up — break now or keep going."
6. **No broadcast** — reading is personal; don't wake other rooms with
   announcements.

## Bedtime-story variant

If triggered as "read a bedtime story" / "絵本を読んで" and the user is
a child (context cue: short utterances, phrasing like "read to me"),
offer to use `retrieve_document` over any RAG-ingested kids' books.
Don't recite a long document without confirming — "I can read from
'The Gruffalo'. Want that?"

## End

Trigger on "reading time over", "done reading", "読書終わり":
1. Restore volume to 50.
2. Restore lights to 80 % warm.
3. Speak: "Hope it was a good one."

## Style

- Gentle, library-voice volume. Never urgent.
- Keep spoken confirmations to a single phrase.

## Tools used
- `execute_command` (light + media_player)
- `set_volume`
- `clear_notifications`
- `set_timer` (optional)
- `retrieve_document` (optional, for bedtime-story variant)
