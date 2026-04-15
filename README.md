# open-smart-speaker

An open-source Android smart speaker app that turns any tablet into a voice-controlled smart home hub. All AI processing runs on-device — no cloud services required.

Say "Hey Speaker" and control your lights, switches, and home devices with your voice.

## Features

- **On-device AI**: Runs Gemma 4 E2B locally via MediaPipe LLM Inference. No internet needed for conversations.
- **Wake word activation**: Always listening for "Hey Speaker" using Vosk (auto-downloaded on first launch).
- **Voice conversation**: Speak naturally. The app understands your intent, controls devices, and responds aloud.
- **Smart home control**: Supports SwitchBot, Matter, MQTT (Shelly/Tasmota), and Home Assistant devices.
- **Dashboard**: Visual grid of all connected devices with tap-to-toggle controls.
- **Ambient display**: Large clock with temperature and humidity from connected sensors.
- **Multiple AI backends**: Switch between on-device LLM, OpenClaw, or any OpenAI-compatible endpoint.
- **Encrypted storage**: All tokens and secrets stored with AES256-GCM encryption.

## Supported Devices

| Protocol | Devices |
|----------|---------|
| SwitchBot | Bot, Curtain, Plug, Color Bulb, Strip Light, Ceiling Light, Lock, Meter |
| Matter | Any Matter-certified device (via Android Matter API) |
| MQTT | Shelly, Tasmota, and other MQTT-discoverable devices |
| Home Assistant | All HA-managed devices (optional, for users with an HA server) |

## Requirements

- Android tablet (Android 9+, 8GB RAM recommended)
- Smart home devices to control (SwitchBot, Matter, MQTT, or HA)

## Setup

1. Install the APK on your tablet
2. Grant microphone permission
3. Open **Settings** and configure your device providers:
   - **SwitchBot**: Enter your API Token and Secret Key
   - **MQTT**: Enter your broker URL (e.g. `tcp://192.168.1.100:1883`)
   - **Home Assistant**: Enter base URL and Long-Lived Access Token (optional)
4. For on-device AI: place a Gemma 4 E2B `.task` model file in the app's `files/models/` directory
5. Say "Hey Speaker" or tap the microphone button

## Building from Source

```bash
# Build
./gradlew assembleDebug

# Test (31 unit tests)
./gradlew test
```

Requires JDK 17 and Android SDK Platform 35.

## Tech Stack

- **Kotlin 2.1.0** with Jetpack Compose and Material 3
- **MediaPipe LLM Inference** for on-device AI (no NDK required)
- **Vosk** for offline wake word detection
- **OkHttp 4.12.0** for REST, SSE streaming, and WebSocket
- **Room 2.6.1** for conversation history persistence
- **Hilt** for dependency injection
- **Eclipse Paho** for MQTT client
- **Moshi** for JSON serialization

## Project Structure

```
app/src/main/java/com/opensmarthome/speaker/
├── assistant/              # AI provider abstraction and routing
│   ├── provider/embedded/  # On-device LLM (MediaPipe)
│   ├── provider/openai/    # OpenAI-compatible REST+SSE
│   ├── provider/openclaw/  # OpenClaw WebSocket
│   └── router/             # Provider selection (Auto/Manual/Failover/LowestLatency)
├── device/                 # Smart home device control
│   ├── provider/matter/    # Matter device control
│   ├── provider/switchbot/ # SwitchBot API client
│   ├── provider/mqtt/      # MQTT discovery and control
│   └── tool/               # LLM function calling for device operations
├── voice/                  # Voice pipeline (wake word → STT → AI → TTS)
├── ui/                     # Chat, Dashboard, Ambient, Settings screens
├── service/                # Foreground service for always-on voice
└── data/                   # Room database, encrypted preferences
```

## License

MIT
