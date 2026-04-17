---
name: hydration-reminder
description: Low-friction hourly nudge to drink water — warm, brief, and easy to dismiss.
---
# Hydration Reminder

Trigger on "hydration reminder", "remind me to drink water", "water
reminder", "水分補給のリマインド", "水飲むの忘れそう", "1時間ごとに水
を思い出させて". Distinct from `stretch-break` (movement, 5-minute
routine) and `focus-mode` (deep-work session): hydration-reminder is a
**recurring, single-sentence nudge** — no routine, no lights, no media
changes. Just a warm reminder to take a sip.

## Default flow on activation

1. **One-sentence confirmation** at the user's current volume — do not
   bump volume on activation. Say "Okay, I'll remind you every hour."
   (JA: 「了解、1時間ごとに声かけしますね。」)
2. **Remember the session** via `remember` with key
   `hydration_reminder.active` and value `true`. Also store
   `hydration_reminder.started_at` with the current timestamp so the
   agent can recall how long the session has been running if the user
   asks later.
3. **Schedule the first nudge** via `set_timer` with
   `{ seconds: 3600, label: "hydration" }`. The label lets us find and
   cancel this specific timer without affecting cooking / laundry
   timers.
4. **Do not fan out to other rooms.** Hydration is a personal habit —
   other speakers should not announce this. Explicitly avoid
   `broadcast_tts`, `broadcast_timer`, `broadcast_announcement`.

## When the timer fires

1. **Check quiet-hours first.** Call `recall` with key
   `quiet_hours.active`. If `true`, **skip this nudge** silently —
   do not speak, do not bump volume, do not reschedule a loud reminder.
   Instead, reschedule the next timer for 3600 seconds later and let
   the next wake cycle handle it. The user explicitly opted into quiet
   hours; hydration never overrides that.
2. **Short warm nudge**, one sentence, at the user's current volume
   (no `set_volume` call — respect whatever they last set):
   - EN: "Quick sip of water?"
   - EN alternates: "Time for some water.", "Hydration check — grab a
     glass?", "Little water break?"
   - JA: 「水分補給のタイミングです。」
   - JA alternates: 「一口、水はどうですか？」、「コップ一杯、いかがで
     すか？」
3. **Optional gentle notification bump.** If the user is likely not
   nearby (no recent voice activity in the last ~10 minutes), call
   `execute_command` with
   `{ device_type: "notification", action: "post", parameters:
   { title: "Hydration", body: "Time for water." } }` so a passing
   glance at the tablet picks it up. Do not escalate — one
   notification, no sound override.
4. **Reschedule** via `set_timer` with
   `{ seconds: 3600, label: "hydration" }` for the next hour.

## While active

- Rotate the nudge phrase between calls so it doesn't feel robotic.
  Keep a small internal pool (4–6 EN, 3–4 JA) and cycle.
- Never follow the nudge with a question that demands a response —
  the user should be able to ignore the nudge entirely with zero
  friction. No "did you drink it?" follow-up.
- If the user responds "thanks" / "drinking now" / 「飲んだ」, simply
  reply with a one-word acknowledgement: "Nice." / 「いいですね。」
- If the user says "skip this one" / 「今回はパス」, say nothing more
  and let the next hour's timer handle it.

## End-of-session

Trigger on "stop hydration reminders", "hydration off", "水の通知を
止めて", "もういいよ":

1. Call `cancel_all_timers` scoped to the hydration label, or the
   more specific `cancel_timer` if available.
2. Update `remember` with key `hydration_reminder.active` and value
   `false`.
3. One short sign-off: "Okay, stopping hydration reminders."
   (JA: 「了解、水の声かけ止めますね。」)

## Interaction with quiet-hours

If the user enables `quiet-hours` while hydration reminders are
running, **do not auto-cancel** the hydration session. The next
fire-time check (step 1 of "When the timer fires") handles the
suppression. When quiet-hours ends, the next hydration timer fires
normally — no catch-up for missed nudges.

If the user asks "did I drink water during the night?" in the
morning, the agent can answer honestly: "I held off while quiet
hours were on."

## Style

- Tone: warm friend, not a drill sergeant. Short. No guilt, no
  exclamation marks, no "you should" phrasing.
- Keep each nudge ≤ 8 words in EN, ≤ 20 characters in JA.
- JA register: polite-casual ("〜です" is fine, "〜してください" is too
  stiff for a habit nudge).
- Never use emoji in TTS text.

## Tools used
- `set_timer`
- `cancel_all_timers`
- `remember`
- `recall`
- `execute_command` (optional, notification post only)

## Tools explicitly avoided
- `broadcast_tts`, `broadcast_timer`, `broadcast_announcement` —
  hydration is personal, no fan-out
- `set_volume` — never override the user's current volume for a
  reminder this low-stakes
