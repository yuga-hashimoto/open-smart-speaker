# Proactive rules

The agent surfaces `Suggestion`s without user prompt when a
`SuggestionRule` decides the current context warrants one. Rules are
composable and cheap to add — this page catalogues what ships, when
each one fires, and the conventions every new rule must follow.

## Rule catalogue

| Rule                          | Hours / trigger                  | Priority  | Action surfaced                            |
| ----------------------------- | -------------------------------- | --------- | ------------------------------------------ |
| `MorningGreetingRule`         | 06:00 – 09:59                    | LOW       | `get_weather`                              |
| `MorningBriefingSuggestionRule` | Weekdays 07:00 – 09:59         | NORMAL    | `morning_briefing`                         |
| `WeekendMorningRule`          | Sat/Sun 08:00 – 11:59            | LOW       | `get_forecast`                             |
| `EveningLightsRule`           | 18:00 – 22:59                    | LOW       | `execute_command` (dim lights to 30%)      |
| `EveningBriefingRule`         | 18:00 – 21:59                    | NORMAL    | Short evening recap                        |
| `NightQuietRule`              | 22:00 – 05:59                    | LOW       | `set_volume` (low cap)                     |
| `ForgotLightsAtBedtimeRule`   | 22:00 – 02:59 + any light on     | NORMAL    | `execute_command` (turn off all lights)    |
| `StalePeerRule`               | Multi-room peer `Gone` status    | NORMAL    | —                                          |
| `LowBatteryRule`              | Battery ≤ 20% while unplugged    | NORMAL / HIGH | — (physical action)                    |
| `ChargingCompleteRule`        | Battery = 100% while charging    | LOW       | — (physical action)                        |

Hours are local time from `ProactiveContext.hourOfDay`.

## `ProactiveContext`

Each `evaluate` call receives:

```kotlin
data class ProactiveContext(
    val nowMs: Long,
    val hourOfDay: Int,
    val dayOfWeek: Int,
    val deviceStates: Map<String, Any?> = emptyMap(),
    val lastUserInteractionMs: Long? = null,
)
```

`deviceStates` and `lastUserInteractionMs` are reserved for future
expansion; rules currently reach into singletons (BatteryMonitor,
DeviceManager, PeerLivenessTracker) via constructor-supplied lambdas
so they stay pure-JVM testable.

## Conventions every new rule follows

### 1. Take dependencies as suppliers, not singletons

```kotlin
// GOOD
class LowBatteryRule(
    private val statusSupplier: () -> BatteryStatus,
) : SuggestionRule { ... }

// The DI wiring lives in DeviceModule:
LowBatteryRule(statusSupplier = { batteryMonitor.status.value })
```

Tests feed synthetic values without standing up the underlying
singleton, which keeps them pure-JVM. See
[`docs/testing-conventions.md`](testing-conventions.md) for the
MockK / runTest patterns that pair with this.

### 2. Dedupe via a stable `Suggestion.id`

`SuggestionState` hides suggestions the user has dismissed by id.
Choose an id that is:

- **Stable across ticks** for the same underlying state (otherwise
  the suggestion reappears every second).
- **Sensitive to state change** so the nudge returns when the
  situation evolves (e.g. `forgot_lights_$joinedNames` changes when
  the set of on-lights changes).

### 3. Pick a priority honestly

- **LOW** — nice-to-have. Battery at 100%, dim-the-lights offer,
  casual greeting. Gets crowded out if anything else fires.
- **NORMAL** — worth reading. Morning briefing, bedtime lights-on,
  peer gone. Default choice.
- **HIGH** — actionable warning. Battery critical, extreme thermal
  throttle. Use sparingly — too many HIGHs desensitise the user.
- **URGENT** — reserve for safety. Not currently used; a smoke
  detector / fall-detection integration would earn it.

### 4. Attach `expiresAtMs` when the underlying state is transient

Rules whose context can change inside a few minutes (battery,
thermal, peer liveness) should carry an expiry so `SuggestionState`
refreshes the card if the situation evolves. Long-lived context
(time-of-day rules) can leave `expiresAtMs = null`.

### 5. Don't act — suggest

`suggestedAction` is the **user's** tap-to-accept payload. A rule
that directly mutates device state bypasses user consent. Stick to
emitting suggestions; let the tap run the tool.

Exceptions:
- `StalePeerRule` — no action; informational only.
- `LowBatteryRule` / `ChargingCompleteRule` — no action because the
  user has to physically plug or unplug the cable.

### 6. Keep evaluate fast

Rules run on every tick of `SuggestionEngine`. They should return in
<5ms of wall clock. Avoid network calls, DB reads, or heavy parsing.
If the data requires work to fetch, cache it in a StateFlow-backed
monitor and read `monitor.status.value` from the supplier.

## Adding a new rule

1. Create `app/src/main/java/.../assistant/proactive/YourRule.kt`
   extending `SuggestionRule` with a supplier-based constructor.
2. Write a unit test under the same package: `YourRuleTest` covering
   fire / don't-fire / boundary / dedupe-id-stability.
3. Register in `DeviceModule.provideSuggestionEngine` — add a new
   entry to the `rules = listOf(...)` block.
4. No changes to `SuggestionEngine` itself. The engine stays dumb; it
   runs every rule on every tick.

## Anti-patterns

- **Frequent-tick side effects.** Don't do anything in `evaluate`
  other than return a suggestion. Logging is fine; state mutation is
  not.
- **Raw singletons in constructor.** Makes the rule JVM-untestable
  because the singleton pulls in Android BroadcastReceiver /
  LocationManager / etc. Always wrap in a supplier lambda.
- **Overlapping rules firing together.** If two rules cover
  near-identical conditions, only one suggestion gets surfaced per
  tick (by priority). Keep conditions disjoint so the user doesn't
  see a churning card.

## Related

- [`SuggestionRule.kt`](../app/src/main/java/com/opendash/app/assistant/proactive/SuggestionRule.kt)
- [`SuggestionEngine.kt`](../app/src/main/java/com/opendash/app/assistant/proactive/SuggestionEngine.kt)
- [testing-conventions.md](testing-conventions.md) — MockK / runTest patterns.
- [home-dashboard.md](home-dashboard.md) — the UI surface for Suggestions.
