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

## Speech-to-Text providers

`DelegatingSttProvider` routes `startListening()` to the backend selected in
**Settings → Speech Recognition**. The selection is stored in
`PreferenceKeys.STT_PROVIDER_TYPE` and resolved through `SttProviderType`.

| Provider | Id | Offline | Status | Notes |
|---|---|---|---|---|
| AndroidSttProvider | `android` | no | shipping | `android.speech.SpeechRecognizer`, GMS-backed; default |
| Vosk (offline) | `vosk` | yes | coming soon | `OfflineSttStub` — emits a spoken "coming soon" error via `ErrorClassifier` |
| Whisper (offline) | `whisper` | yes | coming soon | `OfflineSttStub` — placeholder for whisper.cpp JNI bindings |

Tracked by roadmap **P14.1**. The stubs let the Settings UI and routing code
ship today; future whisper.cpp / Vosk PRs only touch implementation files.

## Text-to-Speech providers

`TtsManager` picks the active backend from `PreferenceKeys.TTS_PROVIDER`
(**Settings → Text-to-Speech**). All backends implement `TextToSpeech`.

| Provider | Id | Local | Status | Notes |
|---|---|---|---|---|
| AndroidTtsProvider | `android` | yes | shipping | `android.speech.tts.TextToSpeech`, default |
| OpenAiTtsProvider | `openai` | no | shipping | `/v1/audio/speech`, configurable voice + model |
| ElevenLabsTtsProvider | `elevenlabs` | no | shipping | Cloud neural voice, voice-id + model selectable |
| VoiceVoxTtsProvider | `voicevox` | yes* | shipping | Self-hosted VOICEVOX ENGINE on LAN; Japanese |
| PiperTtsProvider | `piper` | yes | coming soon | On-device neural (VITS) placeholder — falls back to Android system TTS today |

\* VOICEVOX runs locally on the user's LAN but requires a separate engine
process (Docker / PC).

Tracked by roadmap **P14.9**. The `piper` option is live in Settings so users
can opt in once the piper-cpp JNI bindings land — no routing change needed.
