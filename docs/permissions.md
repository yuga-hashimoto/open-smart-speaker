# Permissions

`PermissionCatalog` lists every runtime permission and special grant the agent
can ask for, with the tools each one unlocks.

## Runtime permissions

| Permission | Unlocks | Required? |
|---|---|---|
| `RECORD_AUDIO` | voice_input | yes (not optional) |
| `READ_CALENDAR` | get_calendar_events | optional |
| `READ_CONTACTS` | search_contacts, list_contacts | optional |
| `ACCESS_COARSE_LOCATION` | get_location | optional |
| `SEND_SMS` | send_sms | optional |
| `CAMERA` | take_photo | optional |
| `READ_MEDIA_IMAGES` (or `READ_EXTERNAL_STORAGE` on pre-T) | list_recent_photos | optional |

## Special grants

Requested via a Settings intent because they can't be granted at runtime.

| Grant | Unlocks | Intent |
|---|---|---|
| Notification listener | list_notifications, clear_notifications | `ACTION_NOTIFICATION_LISTENER_SETTINGS` |
| Accessibility | read_screen | `ACCESSIBILITY_SETTINGS` |

## UI

- **First run** → `OnboardingScreen` walks through the catalog once.
- **Any time** → Settings → Permissions shows every row with current grant
  state, rationale, and a deep-link to the right settings page.
- `PermissionIntents.appDetails(context)` opens the per-app details page for
  runtime permissions that were permanently denied.
