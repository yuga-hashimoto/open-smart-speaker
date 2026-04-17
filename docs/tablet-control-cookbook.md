---
title: Tablet control cookbook
---

# Tablet control cookbook

Recipes for the Phase 15 stack — voice-first tablet automation **with no root**. Every recipe below works with a stock, non-rooted Android tablet. The only privileged grants involved are Accessibility and (optionally) Device Admin, both of which the user enables via the system Settings app.

## Before you start

Grant these once in system Settings, driven by the first-run onboarding:

| Grant | Where | Why |
|---|---|---|
| Accessibility → OpenSmartSpeaker | Settings → Accessibility → Installed services | Read what's on screen, tap buttons, type into fields |
| Notification access | Settings → Apps → Special app access → Notification access | List and reply to notifications |
| Device Admin (optional) | Settings → Security → Device admin apps | `lock_screen` tool only; no other privileges are requested |
| Microphone, Location, Calendar, etc. | Onboarding flow | Tool-specific runtime permissions |

No root. No factory image flashing. No custom ROM.

## Recipes

### Open any app by name

> "Open the weather app" / "Chromeを開いて" / "天気アプリ開いて"

Routes through `LaunchAppMatcher` → `launch_app`. Fuzzy matching via `AppNameMatcher` accepts hint suffixes (`app` / `アプリ`), token reordering, and a bit of typo tolerance. A mumble won't randomly launch Netflix — MIN_SCORE = 60.

### Jump to a system Settings page

> "Open Wi-Fi settings" / "明るさ変えたい" / "音量の設定"

Routes through `SettingsMatcher` → `open_settings_page` → `Settings.ACTION_*`. Supported pages: wifi, bluetooth, display, brightness, sound, volume, accessibility, notifications, apps, battery, home. No accessibility grant required — pure Intent dispatch.

### Read what's on screen (for the LLM to reason about)

> User: "What does this screen say?"
> LLM calls `read_active_screen`.

The tool dumps the current window's accessibility node tree as a markdown-ish list (text, role, bounds, clickable?). Depth cap 8, node cap 200. Requires accessibility grant.

### Tap a button by its visible text

> User: "Tap the blue Save button."
> LLM calls `tap_by_text { text: "save" }`.

Walks the node tree, finds the first clickable node whose text or contentDescription contains the query (case-insensitive), dispatches a GestureDescription tap at its centre. Requires accessibility grant.

### Scroll / swipe the current screen

> "Scroll down", "swipe left", "下にスクロール"

`scroll_screen { direction: "down" }` → GestureDescription swipe across 60 % of the visible area.

### Type text into a focused field

> "Type my email address into the field"
> LLM calls `type_text { text: "example@example.com" }`.

Uses `performAction(ACTION_SET_TEXT)`; falls back to clipboard + paste when the app rejects SET_TEXT.

### Open a URL

> "Open https://github.com" / "go to example.com"

`OpenUrlMatcher` → `open_url`. HTTP/HTTPS allow-list; `intent://`, `content://`, `javascript:`, and `file://` schemes are refused.

### Reply to a notification

> "Reply to LINE from Alice with: on my way"
> LLM calls `reply_to_notification { key: "...", text: "on my way" }`.

Uses `RemoteInput.addResultsToIntent` on the notification's reply action. Silently fails when the notification has no reply action (`"I couldn't reply to that message."`).

### Lock the screen

> "Lock the screen", "画面をロック"

`LockScreenMatcher` → `lock_screen` → `DevicePolicyManager.lockNow()`. Opt-in — requires Device Admin. Fails loudly with a spoken "please enable Device Admin" hint if the user hasn't granted.

### Trigger a voice session from Quick Settings

Drag the **Talk** tile into your QS strip once (system → edit tiles). Tap any time to fire `ACTION_START_LISTENING` — same path as the wake word.

### Pin a routine to the home screen

Long-press the app icon. Up to 4 dynamic shortcuts appear — one per user routine (morning / goodnight / etc.). Tap a shortcut → `MainActivity` fires `run_routine` with the saved steps.

## Composing recipes

Most real flows chain tools. Example: **"open the email app, reply to the latest message"**:

```
launch_app { app_name: "Gmail" }
read_active_screen
tap_by_text { text: "reply" }
type_text { text: "<AI-drafted reply>" }
tap_by_text { text: "send" }
```

The LLM sequences the calls; the fast-path handles the first voice utterance; accessibility drives every tap/type.

## What isn't (yet) possible

- Reading or writing across app sandboxes (no root → no direct FS access into other apps)
- Changing OEM-locked system settings like modem radio
- Running background work while the device is locked (our foreground service is microphone-bound)

See `docs/state-of-the-project.md` for the broader "what works vs scaffold" view.
