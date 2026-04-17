---
name: stretch-break
description: Short movement break after long sitting — 5-minute guided stretch routine with gentle voice cues.
---
# Stretch Break

Trigger on "stretch break", "movement break", "need a break", "ストレッチ
休憩", "体を伸ばしたい". Distinct from `workout` (full exercise session)
and `focus-mode` (stay sitting, work): stretch-break is a 5-minute
standing micro-routine that doesn't interrupt the rest of the day.

## Default flow

1. **Brief kickoff**: speak "Standing for five minutes. Ready?" at
   volume 40 — loud enough to hear from across the room in case the
   user stepped away from the desk.
2. **Set a 5-minute backstop timer** via `set_timer` with
   `{ seconds: 300, label: "stretch" }` so the user isn't locked into
   the routine if they get interrupted.
3. **Lights**: raise to 70 % if they were below 50 % — the user
   probably needs to see where they're going. Don't change colour temp.
4. **Pause media** via `execute_command action:"media_pause"` on any
   active media_player. Cooking audiobook / meeting audio can resume
   after.
5. **Guidance**: the agent calls out each stretch for ~45 s each:
   - 0:00 — neck rolls, shoulders
   - 0:45 — reach overhead + side bends
   - 1:30 — hip circles / standing pigeon
   - 2:15 — calf stretch, hamstring reach
   - 3:00 — chest opener + deep breath
   - 3:45 — walk around the room, shake it out
   - 4:30 — final deep breath, return to desk

   Speak each cue at volume 40, one sentence, gentle imperative.
   Between cues, silence. Don't play music — user needs to hear the
   next cue.

## At end-of-timer

Timer fires, agent says: "Back to it." Volume restores to user's
prior setting (remember it at step 1, restore on end).

## Interrupt handling

If the user says "enough" / "done" / "もうやめる" mid-session:
1. `cancel_all_timers` scoped to the stretch session.
2. Speak briefly: "Okay." — no judgement.
3. Restore lights + volume as above.

## Multi-room

Do NOT broadcast this to other rooms. Stretch breaks are individual.
If a household wants synchronised breaks they'd explicitly ask, and
that would be a follow-up `broadcast_tts` on the user's part — we
don't autoscale.

## Style

- Tone: calm gym-class instructor, not cheerleader.
- JA: polite-casual ("〜してください" not "〜して").
- Don't count reps — count breaths.

## Tools used
- `set_timer`
- `cancel_all_timers`
- `set_volume`
- `execute_command` (light + media_player)
