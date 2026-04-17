# Random tools reference

Three tiny randomness primitives live in `RandomToolExecutor`
(`app/src/main/java/com/opensmarthome/speaker/tool/info/RandomToolExecutor.kt`):
`flip_coin`, `roll_dice`, and `pick_random`. They look trivial, but giving
the LLM real, seedable randomness matters: on-device models routinely
hallucinate biased or repetitive "random" answers ("always heads",
"always the first option"), and asking the LLM to invent entropy burns
tokens for a task a single `Random.nextInt` can answer in microseconds.
Routing these to a tool also lets the fast-path skip the LLM entirely
for the common "flip a coin" utterance, keeping the smart-speaker feel
Alexa-tight (priority 1).

All three primitives are seedable via the executor's constructor
(`Random = Random.Default`), so tests can pin the output deterministically.
No network, no state, stays on-device.

## Tools

| Tool | Args | Returns | Example utterances |
|------|------|---------|--------------------|
| `flip_coin` | _none_ | `{"result":"heads"\|"tails"}` | "flip a coin", "coin toss please", "コインを投げて" |
| `roll_dice` | `sides` (int, 2..100, default 6), `count` (int, 1..10, default 1) | `{"rolls":[...],"sum":N,"sides":N}` | "roll a d20", "roll 3 dice", "サイコロ振って" |
| `pick_random` | `options` (comma-separated string, required) | `{"result":"<choice>"}` | "pick one: pizza, sushi, ramen", "どれにしよう、映画かゲームか散歩" |

### `flip_coin`

```
call:   flip_coin({})
result: {"result":"heads"}
```

- Invalid args: none — takes no parameters.
- Fairness: backed by `kotlin.random.Random.nextBoolean()`.
- Sample utterances that reach this tool:
  - "flip a coin"
  - "toss a coin for me"
  - "コインを投げて"

### `roll_dice`

```
call:   roll_dice({"sides": 20})
result: {"rolls":[17],"sum":17,"sides":20}

call:   roll_dice({"sides": 6, "count": 3})
result: {"rolls":[4,1,6],"sum":11,"sides":6}
```

- `sides` must be in `2..100`; outside that range returns an error result.
- `count` must be in `1..10`; outside that range returns an error result.
- Returns every roll _and_ the sum so the LLM can phrase either
  ("you rolled a 4, a 1, and a 6 — that's 11").
- Sample utterances:
  - "roll a d20"
  - "roll three six-sided dice"
  - "サイコロを3つ振って"

### `pick_random`

```
call:   pick_random({"options": "pizza,sushi,ramen"})
result: {"result":"sushi"}
```

- `options` is split on `,`, each entry trimmed, empty entries dropped.
- Empty or all-whitespace `options` returns an error ("options must
  contain at least one non-empty entry").
- The picked string is JSON-escaped in the return envelope, so options
  containing quotes or backslashes don't corrupt the response.
- Sample utterances:
  - "help me pick dinner — pizza, sushi, or ramen"
  - "choose one: movie, game, walk"
  - "どれにしよう、映画かゲームか散歩"

## Fast-path matchers

Only `flip_coin` has a fast-path matcher today:

| Matcher | Patterns | Tool | Source |
|---------|----------|------|--------|
| `FlipCoinMatcher` | "flip a coin", "toss a coin", "コインを投げて", "コインを振って" | `flip_coin` | `app/src/main/java/com/opensmarthome/speaker/voice/fastpath/FastPathMatchers.kt` |

The `FlipCoinMatcher` uses narrow regexes so compound requests
("flip a coin and order pizza") fall through to the LLM, which can
chain tools across the compound intent. See
[fast-paths.md](fast-paths.md) for the full matcher inventory and the
"narrow match, wide fallback" design rationale.

`roll_dice` and `pick_random` currently go through the LLM — the LLM
picks up the argument list ("roll 3 d20s", "pizza sushi ramen") and
emits a tool call. Adding dedicated matchers would be a priority-1 win
because both utterances are unambiguous and the LLM round-trip adds
200–500ms; track this in [roadmap.md](roadmap.md) when the surface-
volume data justifies it.

## Sample LLM prompts where these tools shine

- "I can't decide between pizza, sushi, and ramen — pick one for me."
  The LLM calls `pick_random(options="pizza,sushi,ramen")` instead of
  "randomly" always saying pizza.
- "Roll me a d20 for initiative." `roll_dice(sides=20)` — on-device,
  no network.
- "Flip a coin to settle this." Fast-path short-circuit, TTS speaks
  "Flipping…" while the tool returns heads/tails in <5ms.
- "Choose a chore from the list: dishes, laundry, vacuuming."
  `pick_random` with a trimmed list.
- "Roll four six-sided dice for D&D stats." `roll_dice(sides=6, count=4)`
  returns all four rolls plus the sum; the LLM narrates.

## See also

- [tools.md](tools.md) — full catalogue of the ~50 LLM-callable tools,
  including the three random primitives (`info/` bucket).
- [fast-paths.md](fast-paths.md) — all voice utterances handled without
  the LLM, including `FlipCoinMatcher`.
- [roadmap.md](roadmap.md) — track candidates for new matchers
  (`RollDiceMatcher`, `PickRandomMatcher`) here.
