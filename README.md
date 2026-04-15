# OpenSmartSpeaker

A tablet-first Android home assistant that combines a smart display UI, always-available voice interaction, Home Assistant integration, and switchable AI backends (OpenClaw / OpenAI-compatible local LLMs).

## Architecture

```
OpenSmartSpeaker
├── Android UI / Smart Display
│   ├── Chat mode
│   ├── Dashboard mode (HA entity cards)
│   └── Ambient mode (clock, weather)
├── Voice Runtime
│   ├── Wake word (Vosk-based)
│   ├── STT (Android SpeechRecognizer)
│   ├── TTS (Android TTS)
│   └── 7-state pipeline with barge-in
├── Conversation Router
│   ├── OpenClawProvider (WebSocket)
│   ├── OpenAiCompatibleProvider (REST + SSE)
│   └── Policy: Manual / Auto / Failover / LowestLatency
├── Tool Execution
│   ├── Home Assistant REST / WebSocket client
│   └── LLM function calling (4 tool schemas)
├── Local Context
│   ├── Room database (sessions, messages)
│   ├── Entity cache (30s refresh)
│   └── Conversation history trimming
└── Discovery
    └── mDNS (HA + OpenClaw)
```

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin 2.1.0 |
| UI | Jetpack Compose + Material 3 |
| DI | Hilt |
| Network | OkHttp 4.12.0 (REST, SSE, WebSocket) |
| JSON | Moshi |
| Database | Room 2.6.1 |
| Preferences | DataStore + EncryptedSharedPreferences |
| Voice | Vosk (wake word), Android STT/TTS |
| Build | Gradle 8.11.1, AGP 8.7.3 |
| Min SDK | 28 (Android 9) |
| Target SDK | 35 |

## Building

Requirements:
- JDK 17
- Android SDK Platform 35

```bash
./gradlew assembleDebug
```

Run tests:
```bash
./gradlew test
```

## Configuration

Launch the app and go to **Settings** to configure:

1. **Home Assistant** - Base URL and Long-Lived Access Token
2. **OpenClaw** - Gateway URL
3. **Local LLM** - OpenAI-compatible endpoint URL and model name

The app auto-discovers Home Assistant and OpenClaw instances on the local network via mDNS.

## Project Structure

```
app/src/main/java/com/opensmarthome/speaker/
├── assistant/          # Provider abstraction, router, session
│   ├── model/          # AssistantMessage, Session, ConversationState
│   ├── provider/       # AssistantProvider interface + implementations
│   │   ├── openclaw/   # OpenClaw WebSocket provider
│   │   └── openai/     # OpenAI-compatible REST provider
│   ├── router/         # ConversationRouter + RoutingPolicy
│   └── session/        # SessionManager, ConversationHistoryManager
├── homeassistant/      # HA client, entity cache, tool schemas
│   ├── client/         # REST + WebSocket clients
│   ├── model/          # Entity, Area, ServiceCall
│   ├── cache/          # EntityCache with periodic refresh
│   └── tool/           # ToolExecutor for LLM function calling
├── voice/              # Voice pipeline
│   ├── wakeword/       # WakeWordDetector + Vosk implementation
│   ├── stt/            # SpeechToText abstraction
│   ├── tts/            # TextToSpeech abstraction
│   └── pipeline/       # VoicePipeline orchestrator (7-state)
├── ui/                 # Compose UI
│   ├── chat/           # Chat screen + ViewModel
│   ├── dashboard/      # Dashboard + EntityCards
│   ├── ambient/        # Ambient clock + weather
│   ├── settings/       # Settings screen
│   ├── navigation/     # App navigation
│   ├── common/         # ModeScaffold, StatusBar
│   └── theme/          # Material 3 theme
├── service/            # Foreground service for always-on voice
├── data/               # Room database + DataStore preferences
├── discovery/          # mDNS service discovery
└── di/                 # Hilt modules
```

## Design Decisions

- **Provider name is `OpenAiCompatibleProvider`**, not `LocalLlmProvider` - supports both local and cloud OpenAI-compatible endpoints
- **Home Assistant is a ToolExecutor**, not a conversation brain - the conversation agent lives in OpenClaw or the local LLM
- **Local LLM via OpenAI-compatible endpoint** (not embedded) - works with MLC LLM, llama.cpp, Ollama, vLLM
- **OpenClaw is special but doesn't shape the shared abstraction** - both providers implement the same `AssistantProvider` interface
- **Tokens stored with AES256-GCM encryption** via EncryptedSharedPreferences
- **Tool-call loop capped at 10 rounds** to prevent infinite loops

## License

MIT
