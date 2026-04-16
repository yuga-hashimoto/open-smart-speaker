# Improvement Roadmap

## Vision
AndroidタブレットをOpenClaw相当のAIエージェント + スマートスピーカーに変える。
ローカルLLMが端末内で自律的にツール実行・デバイス制御・情報取得を行う。

## Current State Analysis

### Working
- AssistantProvider abstraction (3 providers: Embedded, OpenAI-compatible, OpenClaw)
- ConversationRouter with 4 routing policies
- DeviceToolExecutor with 5 tools (get_state, get_by_type, get_by_room, execute_command, get_rooms)
- Device providers (SwitchBot, Matter, MQTT, HA)
- VoicePipeline (wake word → STT → LLM → TTS)
- UI: Chat, Dashboard, Ambient, Settings, Home screens
- Room DB for conversation history
- SecurePreferences for API tokens

### Gaps (vs OpenClaw-like agent)
1. **Tool calling is minimal** — only device control, no system/Android tools
2. **No agent loop** — LLM does one-shot response, no multi-step reasoning
3. **No system prompt** — EmbeddedLlmProvider sends raw user message only
4. **No conversation history** in prompt — only last message sent to LLM
5. **Prompt format is hardcoded** — Gemma format only, no chat template system
6. **No Android-native tools** — no alarms, timers, calendar, contacts, apps, notifications
7. **No web/information tools** — no search, weather, news
8. **No memory/context persistence** — agent forgets everything between sessions

## Phases

### Phase 1: Agent Foundation (Current Priority)
Make the local LLM a proper agent with multi-step reasoning.

- [x] P1.1: System prompt with persona and tool instructions
- [x] P1.2: Full conversation history in prompt (with context window management)
- [x] P1.3: Agent loop — LLM can call tools and continue reasoning
- [ ] P1.4: Chat template system (support multiple model formats)
- [x] P1.5: Tool call parsing improvements (structured JSON output)

### Phase 2: Android System Tools
Give the agent Android-native capabilities.

- [x] P2.1: Timer/Alarm tool (set timer, set alarm, cancel)
- [x] P2.2: Notification tool (read notifications, clear)
- [ ] P2.3: Calendar tool (query events, create reminders)
- [x] P2.4: App launcher tool (open apps by name)
- [x] P2.5: Volume/Media control tool
- [ ] P2.6: Contacts/Phone tool (lookup, call)

### Phase 3: Information Tools
Give the agent access to information.

- [x] P3.1: Weather tool (Open-Meteo, no auth required)
- [x] P3.2: Web search tool (DuckDuckGo Instant Answer API)
- [ ] P3.3: News tool (headlines and summaries)
- [ ] P3.4: Knowledge/FAQ tool (user-defined Q&A)

### Phase 4: Agent Memory & Context
Make the agent persistent and contextual.

- [ ] P4.1: Conversation summarization (compress old context)
- [ ] P4.2: User preference memory (remember user habits)
- [ ] P4.3: Device state context (auto-include relevant device states)
- [x] P4.4: Time/location awareness in system prompt (get_datetime tool)

### Phase 5: Advanced Agent Capabilities
OpenClaw-level autonomy.

- [ ] P5.1: Multi-tool chaining (plan → execute → verify)
- [ ] P5.2: Proactive suggestions (time-based, context-based)
- [ ] P5.3: Routine/automation creation (user-defined workflows)
- [ ] P5.4: Screen control / accessibility service integration

### Continuous: Maintenance
ロードマップ項目がない場合、以下を実施する:
- コードのリファクタリング（重複排除、型安全性向上）
- バグの検出と修正
- パフォーマンス改善
- テストカバレッジ向上

## Improvement Cycle Protocol

Each cycle:
0. **目的確認**: 「AndroidタブレットをOpenClaw相当のAIエージェント+スマートスピーカーに変える。ローカルLLMが端末内で自律的にツール実行・デバイス制御・情報取得を行う」を再読する
1. Pick the next unchecked item from the roadmap (or maintenance task)
2. Create a feature branch
3. Write tests first (TDD)
4. Implement
5. Run `./gradlew test` — fix failures
6. Create PR with description
7. Merge to main
8. Update this roadmap (check off completed items)
9. **目的との整合性チェック**: 今回の変更は最終目標に近づいているか？次に何をすべきか？
