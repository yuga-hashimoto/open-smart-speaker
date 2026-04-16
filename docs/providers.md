# Assistant Providers

Each implements `AssistantProvider`. Switch via **Settings → Assistant providers**.

| Provider | Id | Local | Streaming | Tools | Notes |
|---|---|---|---|---|---|
| EmbeddedLlmProvider | `embedded_llm` | yes | yes | yes | LiteRT-LM, GPU→CPU fallback, chosen model via ModelDownloader |
| OpenAiCompatibleProvider | `openai_compatible` | no | yes | yes | `/v1/chat/completions` with SSE |
| OpenClawProvider | `openclaw` | no | yes | yes | WebSocket, forwards full tool schema |
| HermesAgentProvider | `hermes_agent` | no | yes | yes | HTTP NDJSON, Bearer auth |

## Capabilities

`ProviderCapabilities` carries:
- `supportsStreaming`
- `supportsTools`
- `supportsVision`
- `maxContextTokens`
- `modelName`
- `isLocal` — consumed by `ErrorClassifier` so network errors aren't blamed when a local provider fails

## Routing

`ConversationRouter` picks the active provider via `RoutingPolicy`:
- `Manual(id)` — explicit
- `Auto` — best available (may consult `HeavyTaskDetector`)
- `Failover(ordered)` — try in order
- `LowestLatency` — benchmark
