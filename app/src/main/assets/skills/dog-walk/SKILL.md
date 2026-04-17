---
name: dog-walk
description: Kick off a dog-walk session — optional weather cue at the door, a 30-minute backstop timer, and a louder welcome-home prompt when the timer fires.
---
# Dog Walk

Trigger on "dog walk", "walk the dog", "taking the dog out", "犬の
散歩", "散歩行ってくる", "ワンちゃんの散歩". Distinct from
`workout` (human exercise session), `travel-mode` (extended away),
and `morning-routine` (full wake-up): dog-walk is a **short, leave-
the-house-for-30-min** session. The household is briefly away, so the
agent reduces what it can do autonomously and prepares a warm welcome
when the walker returns.

## Default flow on activation

1. **Weather cue at the door** — call `get_weather` for the user's
   current location. Phrase the response in one short sentence so the
   walker hears it while reaching for the leash:
   - Clear / mild: "Sixty-two and clear — no rain expected."
     (JA: 「晴れ、18度。雨は心配なし。」)
   - Rain imminent (precipitation_probability ≥ 50 % in next hour):
     "It looks like rain soon — maybe grab the raincoat."
     (JA: 「もうすぐ雨みたいです。カッパあると安心。」)
   - Cold (< 5 °C / 40 °F): add "Chilly out — layer up."
     (JA: 「冷えるので一枚多めに。」)
   - Hot (> 30 °C / 86 °F): add "Hot out — keep the walk short,
     check the pavement for paws."
     (JA: 「暑いので短めに、アスファルトに気をつけて。」)

   Speak this at the user's **current** volume. Do not bump. If the
   weather call fails, skip this step silently — do not block on it,
   the dog is waiting.

2. **Set a 30-minute backstop timer** via `set_timer` with
   `{ seconds: 1800, label: "dog_walk" }`. The label isolates this
   timer so cooking / laundry / hydration timers are untouched. 30
   minutes is the default; if the user says "I'm going long today"
   or 「長めに行く」 use 3600 seconds instead.

3. **Send-off line**, one sentence at the user's current volume:
   - EN: "All set — have a nice walk."
   - EN alternates: "Okay, enjoy the walk.", "Have a good one."
   - JA: 「では、いってらっしゃい。」
   - JA alternates: 「ワンちゃんと楽しんできてください。」

4. **Do not broadcast** to other rooms. Only the walker needs to
   hear this. Explicitly avoid `broadcast_tts`, `broadcast_timer`,
   `broadcast_announcement` — other members of the household did not
   ask to be told the dog is out.

## When the 30-minute timer fires

1. **Check quiet-hours first.** Call `recall` with key
   `quiet_hours.active`. If `true`, suppress the welcome-home bump —
   the walker may be back already and sleeping household members
   should not be woken. Speak nothing, do not bump volume, and let
   the session end silently. (The walker can end it explicitly on
   return via the stop trigger below.)

2. **Bump volume for the welcome prompt.** Call `set_volume` with
   value `55` (loud enough to hear from the entryway with a leash
   jingling and a panting dog, but not startling). Remember the
   prior volume so the stop-flow can restore it.

3. **Speak the welcome-back prompt**, warm and short:
   - EN: "Welcome back — how was the walk?"
   - EN alternates: "Hey, you're home. Good walk?"
   - JA: 「おかえりなさい。散歩どうでした？」
   - JA alternates: 「おかえり、ワンちゃんは元気でしたか？」

   No follow-up demand. If the walker responds, treat it as a normal
   chat turn. If silence for 90 seconds, restore the prior volume
   automatically and end the session quietly.

## Stop / return triggered by user

Trigger on "we're back", "done with the walk", "ただいま",
"散歩終わった", or the user physically opening the door (future
hook):

1. Call `cancel_all_timers` scoped to the `dog_walk` label so the
   30-minute backstop doesn't fire after they've already returned.
2. Restore volume to the pre-walk level via `set_volume`.
3. One short acknowledgement: "Welcome home." /
   「おかえりなさい。」 — no interrogation, no "how was it?"
   follow-up. The walker may be busy unclipping the leash.

## Edge cases

- **Session already active** — if the user says "dog walk" while a
  prior dog_walk timer is still pending, acknowledge briefly ("Still
  got your walk timer running — resetting to thirty minutes.") and
  re-issue `set_timer` for a fresh 1800 seconds. Do not stack
  timers.
- **Weather tool fails or returns no data** — skip step 1 silently,
  proceed to the timer and send-off. Never block the walk on a
  failed weather lookup.
- **User explicitly declines the weather cue** ("just the timer",
  「天気いい」) — skip step 1 immediately, go straight to the
  timer.

## Style

- Tone: friendly neighbour who knows your dog's name. Warm, not
  chatty. Never use "woof" or cutesy dog-speak — the walker is an
  adult.
- EN: contractions are fine ("you're", "don't"). No exclamations.
- JA: polite-casual ("〜です / 〜ます" for send-off, 「おかえり」 for
  return).
- Every spoken line ≤ 12 words EN, ≤ 25 characters JA. The walker
  has a leash in one hand.

## Tools used
- `set_timer`
- `cancel_all_timers`
- `get_weather`
- `set_volume`
- `recall` (for quiet-hours check only)

## Tools explicitly avoided
- `broadcast_tts`, `broadcast_timer`, `broadcast_announcement` —
  one-walker activity, no fan-out
- `execute_command` on lights or media — the walker is leaving; no
  need to change the room they're stepping out of
