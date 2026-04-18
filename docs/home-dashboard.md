# Home dashboard (Alexa-style landing)

The Home tab is what the tablet shows when it's sitting on the kitchen
counter doing nothing. The design target is an Echo Show / Nest Hub
front tile: time-first, briefing second, glanceable status third.

## Layout

### Tablet landscape (≥ 600 dp wide)

```
┌────────────────┐  ┌────────────────┐  ┌─────────────────┐  ┌─────────┐
│                │  │                │  │ NextEventCard   │  │ thermal │
│    18:42       │  │ ☀ 22°          │  │ 19:00 Dinner    │  │ chip    │
│   Fri Apr 17   │  │  Clear · Osaka │  ├─────────────────┤  │ (only   │
│                │  │  💧 45% 🌬 8   │  │ ActiveTimersCard│  │ WARM/   │
│                │  │                │  ├─────────────────┤  │  HOT)   │
│                │  │                │  │ DeviceStatusCh. │  │         │
└────────────────┘  └────────────────┘  └─────────────────┘  └─────────┘
┌──────────────────────────────────────────────────────────────────────┐
│  📰  Headlines — auto-advances every 8s                              │
│  [ Alpha headline ] [ Beta headline ] [ Gamma headline ] →            │
└──────────────────────────────────────────────────────────────────────┘
```

Portrait keeps the same widgets but stacks vertically: oversized
clock, weather card, next event / timers / chips, then the headlines
ticker under them. No "Good morning" greeting — the dashboard is
glanceable rather than conversational.

## State sources

Every StateFlow backing the Home screen uses
`SharingStarted.WhileSubscribed(5_000)` so the refresh loop pauses when
the tab is off-screen. Cadences were picked to match how often the
upstream actually changes — the Alexa front tile refreshes on
similar intervals.

| StateFlow                | Refresh   | Source                                        |
| ------------------------ | --------- | --------------------------------------------- |
| `activeTimers`           | 1 s       | `TimerManager`                                |
| `nextEvent`              | 5 min     | `UpcomingEventSource` → `CalendarProvider`    |
| `onlineWeather`          | 15 min    | `OnlineBriefingSource` → `WeatherProvider`    |
| `headlines`              | 25 min    | `OnlineBriefingSource` → `NewsProvider`       |
| `batteryStatus`          | push      | `BatteryMonitor` BroadcastReceiver (consumed by suggestion rules, not the dashboard) |
| `thermalLevel`           | push      | `ThermalMonitor` `OnThermalStatusChangedListener` |
| `weather` (sensor-based) | push      | `DeviceManager.devices` (HA sensors)          |
| `deviceChips`            | push      | `DeviceManager.devices`                       |
| `nowPlaying`             | push      | `DeviceManager.devices`                       |

## Composables

- **`ClockWidget`** — respects `DateFormat.is24HourFormat(context)`.
  12-hour mode shows a muted "AM"/"PM" suffix styled with the colon
  blink alpha. `largeMode = true` on the Home dashboard promotes the
  HH:mm block to ambient-clock size (roughly doubled) so the time is
  the single most prominent element on screen, matching Echo Show /
  Nest Hub.
- **`WeatherWidget`** — legacy sensor-based card (temperature +
  humidity). Rendered when there is no online weather (e.g. offline
  install, geocoding failure).
- **`OnlineWeatherCard`** — upgraded weather tile: condition icon,
  display-size temperature, location + condition text, humidity + wind.
  Shown in place of `WeatherWidget` whenever `onlineWeather != null`.
- **`NextEventCard`** — next upcoming calendar event, locale-aware
  12h/24h time format. Shows "Now" / "いま" for events already running.
  Renders nothing when there is no event (no permanent empty tile).
- **`HeadlinesCard`** — horizontal news tile strip that auto-advances
  one tile every 8 s so the headlines "flow" past the user without
  requiring interaction. Manual flick still works; auto-advance resumes
  from whichever tile is currently first visible. Two-line title +
  three-line summary with ellipsis per tile.
- **`ActiveTimersCard`** — live mm:ss countdown for every running timer.
- **`DeviceStatusChips`** — HA-device chips: lights on, climate,
  playing media.
- **`TabletStatusChips`** — tablet-self thermal-throttle badge. Renders
  nothing in NORMAL state and only appears in WARM/HOT so the warning
  has signal when it shows. (Battery % was previously displayed here
  but was removed — it overlapped with the settings gear icon and the
  Android system status bar already shows charge state.)
- **`NowPlayingBar`** — media control surface: play/pause/next/prev +
  volume + shuffle + repeat + source picker.
- **`SuggestionBubble`** — one proactive suggestion at a time (top).
  Driven by `SuggestionEngine` rules — morning greeting, evening
  lights, low-battery nudge, stale multi-room peer, etc.

## Why these choices

### Priority 1 (smart-home device feel)

- **Time is first, always.** The clock is the highest-contrast element
  and the only widget that animates (colon blink). Everything else
  reads as subordinate.
- **Sensor-less install still looks alive.** A fresh install with no
  HA, no timers, no calendar still shows greeting + clock + online
  weather + headlines — the "Echo Show doing nothing" aesthetic.
- **Warning chips hide when nothing is wrong.** Thermal chip only
  renders in WARM/HOT. Empty data → missing card, not an empty shell.

### Priority 3 (UX polish)

- **`WhileSubscribed(5_000)`** pauses refresh loops when Home is
  off-screen. Settings / Chat tabs don't keep pulling weather.
- **Locale-aware formatting** on greeting, clock, and next-event time.
  24-hour system pref respected; "AM/PM" rendered when 12-hour.
- **Reference-repo credits** tracked in each PR body — the Alexa /
  Echo Show / Nest Hub / ViewAssist VACA / dash-voice sources are
  listed alongside the changes.

## How to add a new tile

1. If the data source is Android-pushed (BroadcastReceiver, HA state),
   expose it from a `@Singleton` monitor and inject the StateFlow
   pass-through into `HomeViewModel`.
2. If the data source is polled (HTTP, provider), wrap it as a
   `SuspendingSource` interface with an `Empty` fallback, add a
   `DefaultXxxSource` impl, and hook it through `@Provides @Singleton`
   in `DeviceModule`. See `OnlineBriefingSource` and
   `UpcomingEventSource` as templates.
3. Emit inside a cold `flow { }` with `stateIn(viewModelScope,
   WhileSubscribed(5_000), initial)` so the refresh loop pauses when
   Home leaves the foreground.
4. Compose the tile as a pure Composable that takes the data as a
   parameter. Avoid coupling the composable to the ViewModel — that
   keeps previews and tests trivial.
5. Slot it into `HomeScreen` following the existing rhythm (right
   column on landscape, vertical stack on portrait).

## Related

- [conventions.md](conventions.md) — Compose + Hilt + Flow patterns.
- [fast-paths.md](fast-paths.md) — voice shortcuts that bypass the LLM.
- [providers.md](providers.md) — assistant-provider abstraction.
