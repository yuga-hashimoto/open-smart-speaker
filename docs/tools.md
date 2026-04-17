# LLM Tools

Tools the on-device agent (or any configured `AssistantProvider`) can call.
All tools are aggregated into one `CompositeToolExecutor` that records usage
analytics per call.

## Device control

| Tool | Source | Notes |
|---|---|---|
| `get_devices` | DeviceToolExecutor | List across HA / SwitchBot / Matter / MQTT |
| `get_device_state` | DeviceToolExecutor | Single device by id |
| `get_devices_by_type` | DeviceToolExecutor | Filter by DeviceType |
| `get_devices_by_room` | DeviceToolExecutor | Filter by Room |
| `execute_command` | DeviceToolExecutor | `{ device_id, action, parameters }` |
| `get_rooms` | DeviceToolExecutor | All known rooms across providers |

## System

| Tool | Source | Notes |
|---|---|---|
| `set_timer` | SystemToolExecutor | `{ seconds, label? }` |
| `cancel_timer` | SystemToolExecutor | `{ timer_id }` |
| `cancel_all_timers` | SystemToolExecutor | |
| `get_timers` | SystemToolExecutor | List active timers (used by ListTimersMatcher fast-path) |
| `set_volume` | SystemToolExecutor | `{ level: 0..100 }` |
| `get_volume` | SystemToolExecutor | |
| `launch_app` | SystemToolExecutor | `{ app_name }` (resolved against installed apps) |
| `list_apps` | SystemToolExecutor | Installed launcher apps |
| `get_datetime` | SystemToolExecutor | Resolves `now` / `today` |
| `find_device` | FindDeviceTool | Rings + vibrates 10s — needs VIBRATE |
| `get_device_health` | DeviceHealthToolExecutor | battery / storage / memory |
| `open_settings_page` | OpenSettingsToolExecutor | `{ page }` — wifi/bluetooth/display/brightness/sound/volume/accessibility/notifications/apps/battery/home |
| `open_url` | OpenUrlToolExecutor | `{ url }` — http/https only; intent://, content://, javascript:, file:// rejected |

## Information

| Tool | Source | Notes |
|---|---|---|
| `get_weather` / `get_forecast` | WeatherToolExecutor | Open-Meteo, no auth |
| `web_search` | SearchToolExecutor | DuckDuckGo Instant Answer |
| `fetch_webpage` | WebFetchToolExecutor | HTML → plain text |
| `get_news` | NewsToolExecutor | Bundled RSS feeds |
| `convert_units` | UnitConverterToolExecutor | length / weight / volume / temp |
| `calculate` | CalculatorToolExecutor | MathEvaluator with unary-minus fix |
| `convert_currency` | CurrencyToolExecutor | exchangerate.host daily rates |
| `get_knowledge` | KnowledgeToolExecutor | User-defined Q&A |

## Communication / I/O

| Tool | Source | Notes |
|---|---|---|
| `send_sms` | SmsToolExecutor | Needs SEND_SMS |
| `search_contacts` / `list_contacts` | ContactsToolExecutor | Needs READ_CONTACTS |
| `get_location` | LocationToolExecutor | Needs ACCESS_COARSE_LOCATION |
| `list_notifications` / `clear_notifications` | NotificationToolExecutor | Needs Notification Listener |
| `reply_to_notification` | NotificationReplyToolExecutor | Sends text reply via `RemoteInput` on the notification's reply action (LINE, Messenger, SMS, ...). `{ key, text }`. Fails gracefully when the notification exposes no reply action. |
| `get_calendar_events` | CalendarToolExecutor | Needs READ_CALENDAR |
| `list_recent_photos` | PhotosToolExecutor | Needs READ_MEDIA_IMAGES |
| `take_photo` | CameraToolExecutor | Uses IntentCameraProvider (real capture) |
| `start_screen_recording` / `stop_screen_recording` | ScreenRecorderToolExecutor | MediaProjection |
| `read_screen` | ScreenToolExecutor | Needs Accessibility service |
| `read_active_screen` | ReadActiveScreenToolExecutor | Markdown dump of foreground window via new-style A11y service (P15.2) |
| `tap_by_text` | TapByTextToolExecutor | `{ text }` — taps clickable node whose text/desc contains query via GestureDescription (P15.3) |
| `scroll_screen` | ScrollScreenToolExecutor | `{ direction }` — up/down/left/right swipe across window centre (P15.4) |
| `type_text` | TypeTextToolExecutor | `{ text }` — ACTION_SET_TEXT on focused input; clipboard paste fallback (P15.5) |

## Agent memory

| Tool | Source | Notes |
|---|---|---|
| `remember` / `recall` / `forget` | MemoryToolExecutor | Room + TF-IDF index |
| `search_memory` / `semantic_memory_search` | MemoryToolExecutor | Keyword / similarity |
| `list_memory` | MemoryToolExecutor | Returns the full memory store |

## RAG

| Tool | Source | Notes |
|---|---|---|
| `ingest_document` | RagToolExecutor | TextChunker + vectorless index |
| `retrieve_document` | RagToolExecutor | TF-IDF retrieval |
| `list_documents` / `delete_document` | RagToolExecutor | |

## Routines

| Tool | Source | Notes |
|---|---|---|
| `run_routine` / `list_routines` / `delete_routine` | RoutineToolExecutor | Room-backed |

## Skills

| Tool | Source | Notes |
|---|---|---|
| `get_skill` / `list_skills` | SkillToolExecutor | |
| `install_skill_from_url` | SkillInstaller | Downloads + validates SKILL.md |

## Multi-room

Bus-routed tools that fan out across every mDNS-discovered OpenSmartSpeaker
peer (or a named subset). All require **Multi-room broadcast** enabled in
Settings and a matching HMAC shared secret on every paired device. See
[multi-room-quickstart](multi-room-quickstart.md) for onboarding and
[multi-room-protocol](multi-room-protocol.md) for the wire format.

| Tool | Source | Notes |
|---|---|---|
| `broadcast_tts` | BroadcastTtsToolExecutor | `{ text, language?, group? }` — speaks on every peer (or just members of the named group). Returns `sent` + `failed` counts |
| `broadcast_timer` | BroadcastTimerToolExecutor | `{ seconds, label? }` — starts a timer on every peer. Clamped 1..86400 |
| `broadcast_announcement` | BroadcastAnnouncementToolExecutor | `{ text, ttl_seconds? }` — speaks once AND pins a banner on the Ambient screen for ttl_seconds (default 60, clamped 5..3600). New in P17 follow-up |
| `handoff_session` | HandoffToolExecutor | `{ target }` — transfers the current conversation to the named peer (replace semantics). Media handoff stubbed |
| `list_peers` | ListPeersToolExecutor | No args — snapshot of `MulticastDiscovery.speakers` as JSON |

## Composite tools

These tools chain several other tools in one call. The agent (and the
fast-path) can invoke them by name; the user perceives one action.

| Tool | Source | Notes |
|---|---|---|
| `morning_briefing` | MorningBriefingTool | weather + news + today's calendar |
| `evening_briefing` | EveningBriefingTool | notifications + tomorrow's calendar + active timers |
| `goodnight` | GoodnightTool | lights off + media pause + cancel timers |
| `arrive_home` | PresenceTool | lights on + volume to 50 |
| `leave_home` | PresenceTool | lights off + media pause |
