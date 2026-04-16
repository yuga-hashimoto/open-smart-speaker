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
| `list_timers` | SystemToolExecutor | |
| `set_volume` | SystemToolExecutor | `{ level: 0..100 }` |
| `adjust_volume` | SystemToolExecutor | `{ delta }` |
| `get_volume` | SystemToolExecutor | |
| `launch_app` | SystemToolExecutor | `{ package_or_name }` |
| `get_datetime` | SystemToolExecutor | Resolves `now` / `today` |
| `get_device_health` | DeviceHealthToolExecutor | battery / storage / memory |

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
| `get_calendar_events` | CalendarToolExecutor | Needs READ_CALENDAR |
| `list_recent_photos` | PhotosToolExecutor | Needs READ_MEDIA_IMAGES |
| `take_photo` | CameraToolExecutor | Uses IntentCameraProvider (real capture) |
| `start_screen_recording` / `stop_screen_recording` | ScreenRecorderToolExecutor | MediaProjection |
| `read_screen` | ScreenToolExecutor | Needs Accessibility service |

## Agent memory

| Tool | Source | Notes |
|---|---|---|
| `remember` / `recall` / `forget` | MemoryToolExecutor | Room + TF-IDF index |
| `search_memory` / `semantic_memory_search` | MemoryToolExecutor | Keyword / similarity |

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
