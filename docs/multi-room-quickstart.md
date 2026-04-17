---
title: Multi-room quickstart
---

# Multi-room quickstart

How to set up two or more OpenSmartSpeaker tablets so one can talk to all of them at once.

## What this gets you

- **Broadcast TTS** — "Broadcast dinner is ready to all speakers" reads the message on every paired tablet.
- **Per-group broadcast** — Name a subset (e.g. `kitchen`, `upstairs`) and route messages only to members.
- **Session handoff** — "Move this to the kitchen speaker" transfers the current conversation.
- **Peer list** — "List nearby speakers" reads out every tablet it can see.

## Prerequisites

- Two or more Android tablets on the same Wi-Fi network.
- App installed on each (same APK is fine).
- No internet required. No cloud account. No router configuration.

## Step 1 — Enable discovery

On **each** tablet:

1. Open **Settings → Multi-room broadcast**.
2. Flip the toggle **on**.

This advertises the device on the LAN via mDNS (`_opensmartspeaker._tcp` on port 8421) and starts the NDJSON listener.

Verify: open **Settings → System Info** on one tablet. The "Nearby speakers (mDNS)" row should list the other tablet within ~3 seconds.

## Step 2 — Set a shared secret

Broadcasts are signed with HMAC-SHA256. Every tablet that trusts each other must use the **same** shared secret.

1. Pick a long random string. `openssl rand -base64 32` or any password manager works. Aim for ≥ 24 characters.
2. On each tablet, paste it into **Settings → Multi-room shared secret**.
3. Confirm the **Pairing phrase** box on each tablet shows the same 4 words. If the phrases match, the secrets match byte-for-byte.

Mismatched secrets mean receivers silently drop every envelope (by design — a rogue sender on guest Wi-Fi never gets through).

## Step 3 — Try it

Say on any tablet:

> "Broadcast dinner is ready to all speakers"

Every tablet speaks "dinner is ready". Sub-200 ms dispatch via fast-path.

Or for a named subset:

> "Broadcast bath time to upstairs"

Groups are managed in **Settings → Multi-room → Speaker groups** (client-side only, never sent on the wire). Add peers by their mDNS service name.

## Common gotchas

| Symptom | Likely cause | Fix |
|---|---|---|
| System Info shows "(none)" under Nearby speakers | mDNS blocked by router isolation or different VLAN | Move both to the same subnet; disable "AP isolation" on your router |
| `adb logcat` shows `Envelope rejected: HMAC_MISMATCH` | Secrets differ | Re-paste identical secret on both; check the Pairing phrase matches |
| `adb logcat` shows `Envelope rejected: REPLAY_WINDOW` | Clock skew > 30 s | Enable automatic network time on both tablets |
| Broadcast says "No peers found" | Discovery toggle off on the other tablet, or firewall | Re-check Settings → Multi-room broadcast on every tablet |
| `Broadcast refused: no shared secret` | Sender's secret is blank | Paste a secret on the sender |

## What happens next

- **Session handoff** (P17.5): "Move this to the kitchen speaker" on device A clears device B's local history and seeds it with the current conversation.
- **Queue sync / media handoff**: not yet wired. Currently only the spoken TTS and conversation transfer.
- **QR-pair** (P17.6): fingerprint phrase lands in this release; full pairing-code exchange is a follow-up.

For the wire format, see [Multi-room protocol](multi-room-protocol.md). For the on-device validation run, see [Real-device smoke test](real-device-smoke-test.md).
