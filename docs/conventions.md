# Coding Conventions

## Kotlin

### Immutability
- Use `val` over `var`
- Use `data class` + `copy()` for state updates
- Use `List` (not `MutableList`) in public APIs
- Collections: prefer `map`, `filter`, `fold` over mutation loops

### Null Safety
- Never use `!!` — use `?.let {}`, `?:`, `requireNotNull()`, or `checkNotNull()`
- Prefer non-nullable types in public APIs
- Use `?.` chains for optional access

### Coroutines
- `suspend fun` for one-shot async operations
- `Flow<T>` for streams (SSE, WebSocket, state changes)
- Use `withContext(Dispatchers.IO)` for blocking I/O
- Structured concurrency: tie coroutines to lifecycle scopes

### Error Handling
- Use `Result<T>` or sealed class for operation outcomes
- Never silently swallow exceptions
- Log errors with `Timber.e()`

### Naming
- Functions: `verbNoun()` — e.g., `sendMessage()`, `fetchDevices()`
- Boolean: `is`/`has`/`should` prefix — e.g., `isConnected`
- Constants: `UPPER_SNAKE_CASE`
- Named arguments when 3+ parameters

## Jetpack Compose

### State Management
- ViewModel owns state via `StateFlow` or `MutableState`
- Screens receive state as parameters (state hoisting)
- Collect in Composable: `collectAsStateWithLifecycle()`

### Component Structure
```kotlin
// Stateless composable (pure UI, preview-friendly)
@Composable
fun DeviceCard(
    device: Device,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
)

// Screen composable (connects to ViewModel)
@Composable
fun DashboardScreen(viewModel: DashboardViewModel = hiltViewModel())
```

### Previews
- Every reusable composable should have a `@Preview`
- Use `@PreviewLightDark` for theme coverage

## Architecture Patterns

### New AssistantProvider
1. Create package under `assistant/provider/{name}/`
2. Implement `AssistantProvider` interface
3. Add config data class: `{Name}Config.kt`
4. Register in `AssistantModule` (Hilt)
5. Add tests with MockWebServer or MockK

### New DeviceProvider
1. Create package under `device/provider/{name}/`
2. Implement `DeviceProvider` interface
3. Register in `DeviceModule` (Hilt)
4. Map device capabilities to `DeviceCapability` enum
5. Add tests

### Adding a UI Screen
1. Create package under `ui/{feature}/`
2. Create `{Feature}Screen.kt` (Composable) + `{Feature}ViewModel.kt`
3. Add navigation route in the nav graph
4. Use Material 3 components

### Adding a Fast-path matcher
1. Append the `object : FastPathMatcher` to
   [`FastPathMatchers.kt`](../app/src/main/java/com/opensmarthome/speaker/voice/fastpath/FastPathMatchers.kt)
2. Slot it into `DefaultFastPathRouter.DEFAULT_MATCHERS` respecting
   precedence — earlier entries win when regexes overlap
3. Add test cases to `FastPathRouterTest`, including a precedence
   guard when your matcher shares substrings with an existing one
4. Update [`docs/fast-paths.md`](./fast-paths.md)
5. Prefer short canned `spokenConfirmation` strings over re-invoking
   the LLM; this is the whole point of the fast-path

### Adding a composite tool
Composite tools chain several existing tools in one call, giving
the fast-path + skills a single name to invoke.
1. Create `tool/composite/{Name}Tool.kt` implementing `ToolExecutor`
2. Take a lambda `toolExecutor: suspend (ToolCall) -> ToolResult` to
   avoid Hilt cycles — the real `CompositeToolExecutor` injects itself
   via the lambda at DI time
3. Register in `DeviceModule.provideToolExecutor`
4. Add a `FastPathMatcher` if the tool should bypass the LLM
5. Add a test in `tool/composite/`

### Adding a bundled skill
1. Drop a `SKILL.md` under `app/src/main/assets/skills/<name>/`
2. Frontmatter must have `name:` and `description:`
3. Body describes trigger words, default flow, and style; keep under
   ~300 tokens to leave prompt budget for the conversation
4. Add a row to the catalog in [`docs/skills.md`](./skills.md)

## File Size
- Target: 200-400 lines
- Maximum: 800 lines
- If larger, extract into separate files by responsibility

## Commit Messages
```
<type>: <description>
```
Types: `feat`, `fix`, `refactor`, `docs`, `test`, `chore`, `perf`
