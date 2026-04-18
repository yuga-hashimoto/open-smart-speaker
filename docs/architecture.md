# Architecture

## Overview

OpenDash is a tablet-first Android smart display and voice terminal.
Three UI modes: Chat (conversation), Dashboard (device grid), Ambient (clock + sensors).

## Module Structure

Single `app` module. Package structure under `com.opendash.app`:

```
assistant/
  model/              AssistantMessage, AssistantSession, ConversationState
  provider/           AssistantProvider interface + implementations
    embedded/         EmbeddedLlmProvider (MediaPipe GenAI on-device)
    openai/           OpenAiCompatibleProvider (REST + SSE streaming)
    openclaw/         OpenClawProvider (WebSocket)
  router/             ConversationRouter + RoutingPolicy enum
  session/            ConversationHistoryManager, SessionManager

device/
  model/              Device, DeviceState, DeviceCommand, DeviceCapability, Room
  provider/           DeviceProvider interface + implementations
    homeassistant/    HomeAssistantDeviceProvider
    matter/           MatterDeviceProvider
    mqtt/             MqttDeviceProvider + MqttClientWrapper
    switchbot/        SwitchBotDeviceProvider + SwitchBotApiClient
  tool/               DeviceToolExecutor (LLM function calling → device ops)

voice/
  pipeline/           VoicePipelineState
  stt/                SpeechToText interface + AndroidSttProvider
  tts/                TextToSpeech interface + AndroidTtsProvider
  wakeword/           WakeWordDetector interface + VoskWakeWordDetector

tool/                 ToolCall, ToolResult, ToolSchema, ToolExecutor interface
homeassistant/        HA-specific: client, cache, model, ToolExecutorImpl
ui/                   Compose screens per feature
service/              VoiceService (foreground service), VoiceServiceNotification
data/                 Room (AppDatabase, DAOs, Entities), SecurePreferences
di/                   Hilt modules (AssistantModule, DatabaseModule, NetworkModule, etc.)
```

## Key Abstractions

### AssistantProvider
Defines the contract for AI conversation backends. Each implementation handles
connection, message sending, and streaming independently.

```
interface AssistantProvider {
    suspend fun sendMessage(message: String): Flow<String>
    suspend fun initialize()
    suspend fun shutdown()
}
```

### ConversationRouter
Selects which AssistantProvider to use based on RoutingPolicy:
- **Manual** — user-selected provider
- **Auto** — picks best available
- **Failover** — falls back on error
- **LowestLatency** — benchmarks and picks fastest

### DeviceProvider
Abstraction for smart home device backends. Each protocol (SwitchBot, Matter,
MQTT, HA) implements this interface.

### ToolExecutor
Bridges LLM function calling with device operations. The LLM emits ToolCall
objects; ToolExecutor maps them to DeviceCommand executions and returns ToolResult.

### VoicePipeline
Orchestrates the full voice interaction loop:
wake word detection → STT → AssistantProvider → TTS

## Data Flow

```
Microphone → VoskWakeWordDetector → AndroidSttProvider
  → ConversationRouter → AssistantProvider → ToolExecutor (if device command)
  → AndroidTtsProvider → Speaker
```

## Dependency Injection

Hilt modules in `di/` package:
- AssistantModule — providers, router
- DatabaseModule — Room, DAOs
- NetworkModule — OkHttpClient
- DeviceModule — device providers, DeviceManager
- VoiceModule — STT, TTS, wake word
- HomeAssistantModule — HA client, cache

## Persistence

- **Room** — conversation history (Sessions + Messages)
- **SecurePreferences** — API tokens, secrets (AES256-GCM encrypted)
- **DataStore** — app settings (non-sensitive)
