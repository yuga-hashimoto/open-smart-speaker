---
name: home-control
description: Controls smart home devices (lights, switches, climate, media) via Home Assistant and other providers.
---
# Home Control Skill

When the user asks to control devices at home, follow these steps:

1. First call `get_rooms` or `get_devices_by_room` / `get_devices_by_type` to discover what's available.
2. Match the user's natural-language reference (e.g. "the bedroom light") to a concrete device id.
3. Call `execute_command` with the device id and desired action.
4. Confirm the action briefly in speech (e.g. "Turned off the bedroom light").

## Best practices
- Prefer `get_device_state` before executing a command when you are unsure of current state.
- When the user says "all lights", filter by type "light" and iterate, or use the scene/group if available.
- When a command may have been issued already, avoid duplicating it.
- If no matching device is found, tell the user exactly which rooms/devices you saw.

## Error handling
- If a provider is offline, try another provider (HomeAssistant → SwitchBot → MQTT) when possible.
- Report errors in plain language, not technical details.
