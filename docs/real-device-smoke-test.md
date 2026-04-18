---
title: Real-Device Smoke Test
---

# Real-Device Smoke Test Checklist

This is the on-device validation run that must pass before a release cut. It exercises the wake → STT → LLM → TTS pipeline end-to-end on representative hardware. CI cannot run it because it needs a microphone, speakers, and Play-services-free environment.

Reference for the latency budgets below: [fast-paths.md](fast-paths.md), [providers.md](providers.md).

## Hardware under test

The primary target is an Android tablet (≥ 600dp landscape). Validate on at least one of each:

- **Tablet (primary)** — Pixel Tablet, Galaxy Tab S-series, or Lenovo P12 class
- **Phone (secondary)** — Pixel 6+, Galaxy S22+, for fallback use
- **AOSP (optional)** — LineageOS on a tablet without Play Services (offline STT path)

Network states to verify each pass:

| State | What to verify |
|-------|----------------|
| Online | Remote provider Auto routing picks remote for heavy tasks |
| Offline | Auto routing falls back to embedded LLM; ConnectionBadge shows offline |
| Flaky Wi-Fi | Filler phrase plays; no perceived hang |

## Smoke journey

Run the following scripted flow. Stop on first failure; file as a bug with the latency value captured from the System Info screen.

### 1. Cold start → home
- Kill the app; relaunch.
- **Pass**: Home screen renders within 2 s; ambient clock ticks; ConnectionBadge reflects network.

### 2. Wake → listening latency
- Speak the wake phrase from 1 m away in a quiet room.
- **Pass**: VoiceOrb transitions to `LISTENING` within **500 ms** (P8.2 budget).
- **Fail trigger**: LatencyRecorder → `WAKE_TO_LISTENING` budget violation in System Info.

### 3. Fast-path command
- Say: "Set a 5 minute timer."
- **Pass**: Spoken confirmation within **200 ms** (FAST_PATH_TO_RESPONSE budget); AmbientScreen shows timer countdown.
- Try the 20+ matchers from [fast-paths.md](fast-paths.md) — at minimum: timer, volume, lights, weather, news, goodnight, morning briefing, help.

### 4. Tool-calling path
- Say: "What's the weather tomorrow in Tokyo?" (skips fast-path, goes through LLM).
- **Pass**: Filler phrase plays within ~1 s; final answer within 3 s on remote provider, 6 s on embedded.

### 5. Barge-in
- Start a long TTS utterance ("Tell me a story about cats").
- Mid-utterance, say the wake phrase.
- **Pass**: TTS halts immediately; VoiceOrb switches to `LISTENING`.

### 6. Error recovery
- Disable Wi-Fi and Cellular.
- Ask a question that requires a remote tool (`web_search`, `get_news`).
- **Pass**: Spoken error uses the offline-friendly copy from ErrorClassifier (`LOCAL_ENGINE` or `NETWORK`); app does not crash.
- Re-enable network; same question should now succeed.

### 7. Tablet landscape
- Rotate to landscape on a ≥ 600dp device.
- **Pass**: Two-column layout renders; touch targets ≥ 48 dp; night mode + NightClockOverlay toggles work.

### 8. Permissions walkthrough
- Fresh install (or `pm clear com.opendash.app`).
- After model download finishes, OnboardingScreen must appear before the mode scaffold.
- **Pass**: Each permission row's "Grant" deep-link opens the correct system screen.

### 9. Long-running stability
- Leave the app in ambient mode for ≥ 30 minutes with wake-word listening active.
- **Pass**: No memory growth visible in System Info; wake word still responds; TTS still plays.

### Multi-room discovery (P14.5)

Verifies the mDNS/NSD plumbing added in PR #209 (discovery) and PR #220 (registration). The broadcast RPC protocol that would let one speaker forward commands to another is **not wired yet** — registration is only the advertise-ourselves signal, not a useful endpoint today.

**Prerequisite**: two devices running the debug build on the same LAN (same Wi-Fi SSID, multicast not blocked by the router — some guest networks drop mDNS).

1. **Discovery from the app**
   - Launch the app on both devices.
   - On device A, open Settings → System Info and watch the **"Nearby speakers (mDNS)"** row.
   - **Pass**: device B (instance name `OpenDash-<Build.MODEL>`) appears in the list within ~3 s of launch. Swap roles and re-verify from device B.
   - **Fail trigger**: row stays on "0 peers" after 10 s — check that both devices are on the same subnet and that `MulticastDiscovery.start()` ran (look for the log tag in `adb logcat | grep MulticastDiscovery`).

2. **Registration from a desktop**
   - From a machine on the same LAN:
     - macOS: `dns-sd -B _opendash._tcp`
     - Linux: `avahi-browse -rt _opendash._tcp`
   - **Pass**: each running instance is listed with its instance name and port `8421` (the `DEFAULT_PORT` from `MulticastDiscovery`).
   - Kill the app on one device; the entry should disappear from the browse output within a few seconds.

Note: no Settings toggle yet invokes `MulticastDiscovery.register(port, instanceName)` — to exercise registration today you need a debug harness or a manual call from a test build. Treat this section as a plumbing check, not a feature validation.

#### Multi-room broadcast end-to-end (P17.2 / P17.3)

Validates the NDJSON listener + sender wired up in PR #241 (server), PR #243 (client/broadcaster), and PR #245 (`broadcast_tts` tool + matcher).

**Prerequisite**: two tablets on the same LAN, both running the app, and **both with the same Multi-room shared secret set** in Settings (the new row added in PR #242).

1. On both devices, enable the **Multi-room broadcast** toggle.
2. On device A, say "broadcast dinner is ready to all speakers" — expect device B to speak "dinner is ready" within ~1 s.
3. **Tamper test**: change the secret on device B only, repeat step 2 — device B should stay silent (`AnnouncementParser` rejects on HMAC mismatch). Check `adb logcat` on device B for `Envelope rejected: HMAC_MISMATCH`.
4. **Replay test**: set both clocks, then artificially skew device B's clock 2 minutes forward. Repeat step 2 — device B should stay silent (`Envelope rejected: REPLAY_WINDOW`).
5. **No-secret test**: clear the secret on device A, repeat step 2. Device A's tool should return a spoken message "Broadcast refused: no shared secret."

Reference: see [`multi-room-protocol.md`](multi-room-protocol.md) for the full protocol.

### 10. System info sanity
- Open Settings → System Info.
- **Pass**: Device count, routines count, documents count, latency measurements count all render without errors. Also verify the new **"Nearby speakers (mDNS)"** and **"Thermal state"** rows render with a live value rather than a placeholder.

## Power & thermal

Run for 15 minutes with wake-word listening active and screen on at 50 % brightness.

- **Thermal**: `adb shell dumpsys thermalservice` should not report STATUS_SEVERE or worse.
- **Battery drain**: record % drop over 15 min; expect < 10 % on a healthy battery. (Tracked by P14.8.)

## Recording the run

Save the journey as a dated markdown note under `docs/smoke-runs/YYYY-MM-DD.md`. Include:

- Device model + Android version
- Latency values for step 2 and step 3
- Pass/Fail per step
- Any budget violations copied from System Info

## When to run

- Before each **tagged release** (P13.4 release workflow).
- After changes to: `VoicePipeline`, `AndroidSttProvider`, `FastPathRouter`, `TtsManager`, `VoskWakeWordDetector`, or anything under `service/`.
