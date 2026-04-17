# Testing conventions

Unit tests ship with every feature (80% target, per `docs/conventions.md`).
This page catalogues the patterns the codebase has converged on and
the landmines we've already hit so new contributors don't re-discover
them the hard way.

## Stack

| Tool                | Why                                                         |
| ------------------- | ----------------------------------------------------------- |
| JUnit 5             | `@Test`, `@BeforeEach`, backtick-named functions            |
| Truth               | Assertions (`assertThat(x).isEqualTo(y)`)                   |
| MockK               | Mocks and stubs for Kotlin interfaces / suspend fns         |
| Turbine             | Flow testing (preferred over manual collector + timeout)    |
| `kotlinx-coroutines-test` | `runTest { }` + `StandardTestDispatcher` + virtual time |
| MockWebServer       | HTTP integration (OkHttp stack, provider tests)             |

## `runTest { }` + virtual time

Every async test that touches coroutines uses `runTest` so we don't
rely on real wall clock. The canonical setup:

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class FooTest {
    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach fun setup() { Dispatchers.setMain(testDispatcher) }
    @AfterEach  fun teardown() { Dispatchers.resetMain() }

    @Test
    fun `something happens after a delay`() = runTest {
        val vm = FooViewModel(deps)
        advanceTimeBy(1_000L)  // virtual ms
        advanceUntilIdle()     // drain any remaining scheduled work
        assertThat(vm.state.value).isEqualTo(expected)
    }
}
```

### `advanceTimeBy` vs `advanceUntilIdle`

**Use `advanceTimeBy`** when the flow under test has an unbounded
`while (true) { emit; delay(X) }` loop. `advanceUntilIdle` keeps
advancing through scheduled work — on a perpetual delay loop it will
never return. Example that hangs:

```kotlin
// BAD — hangs because the loop schedules another delay each iteration
val job = vm.onlineWeather.onEach { }.launchIn(backgroundScope)
advanceUntilIdle()  // spins forever

// GOOD — bounded advance gives exactly one iteration
val job = vm.onlineWeather.onEach { }.launchIn(backgroundScope)
advanceTimeBy(50L)
```

The `WhileSubscribed(5_000)` pattern used on `HomeViewModel` flows is
prone to this — every `stateIn` flow with an infinite body must be
advanced by a bounded delta.

## MockK landmines

### `spyk` on `fun interface` → `NoClassDefFoundError` on JVM 21

```kotlin
// BAD — throws NoClassDefFoundError at runtime on JVM 21
fun interface Clock { fun now(): Long }
val clock = spyk(Clock { 12345L })
```

Use a capturing lambda or a hand-written fake instead:

```kotlin
// GOOD
var now = 0L
val clock = Clock { now }
```

Reason: MockK's JVM 21 + `fun interface` interaction fails to locate
the synthetic class. Hand-written fakes work everywhere.

### `every { x.suspendFun() }` vs `coEvery`

Every `suspend` function **must** use `coEvery { }`:

```kotlin
// BAD — throws "suspend function cannot be called from non-suspend"
every { timer.cancelTimer("id") } returns true

// GOOD
coEvery { timer.cancelTimer("id") } returns true
```

Likewise use `coVerify` (not `verify`) for suspend assertions.

### Mocking `MutableStateFlow` vs returning it

Prefer returning a real `MutableStateFlow` from the mock — you can
drive it from the test:

```kotlin
val flow = MutableStateFlow(BatteryStatus(level = 80, isCharging = true))
every { batteryMonitor.status } returns flow

val vm = HomeViewModel(..., batteryMonitor, ...)
assertThat(vm.batteryStatus.value.level).isEqualTo(80)

flow.value = BatteryStatus(level = 15, isCharging = false)  // drive it
assertThat(vm.batteryStatus.value.isLow).isTrue()
```

Mocking the StateFlow itself (`mockk<StateFlow<X>>()`) is almost never
what you want — the `collect { }` / `value` accessors won't respond
to updates.

## Test file naming & location

- Mirror the main-source package: `app/src/main/java/.../foo/Bar.kt`
  → `app/src/test/java/.../foo/BarTest.kt`.
- One test file per class. Split when a test file exceeds ~200 lines.
- Backtick-quoted test names:
  `` fun `returns null when permission missing`() = runTest { } ``.
- Helper fake classes go inside the test file as private nested classes
  unless re-used elsewhere.

## What we don't do

- **Robolectric**: not added. Android-API-touching code gets a thin
  pass-through interface + fake in tests (see `CalendarProvider`,
  `WeatherProvider`, `BatteryMonitor` patterns).
- **Instrumented tests** (`androidTest/`): minimal — we prefer pure
  JVM coverage. Instrumented is only for widget-preview-type smoke
  runs and isn't gated in CI.
- **Compose UI tests**: not yet. The composables take data as
  parameters so their logic is tested via the ViewModel feeding them;
  visual assertions are handled by the real-device smoke test.

## Coverage

```bash
./gradlew jacocoTestReport
open app/build/reports/jacoco/jacocoTestReport/html/index.html
```

Excludes UI / generated Hilt / Moshi / Room / Compose code. Targets
80% on the non-UI code paths.

## Related

- [conventions.md](conventions.md) — Kotlin style, package layout.
- [real-device-smoke-test.md](real-device-smoke-test.md) — manual
  on-device verification steps.
- [latency-budgets.md](latency-budgets.md) — what the voice-path
  tests measure.
