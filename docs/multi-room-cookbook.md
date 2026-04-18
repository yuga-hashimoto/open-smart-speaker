---
title: Multi-room cookbook
---

# Multi-room cookbook

Recipes for every Phase 17 multi-room tool shipped to date. Read
[multi-room-quickstart](multi-room-quickstart.md) first to pair two
tablets; this page assumes that's already done.

## Broadcast a spoken message

> "Broadcast dinner is ready to all speakers"
> "キッチンにご飯だよってアナウンス"

Goes through `broadcast_tts`. Accepts an optional `group` argument that
maps to a Settings → Multi-room → Speaker groups entry. If the message
names a group that doesn't exist you'll hear "No such group X".

## Pin a banner on every speaker

> "Announce 'house meeting at 7' to every speaker"

Goes through `broadcast_announcement`. Speaks **and** pins a card on the
Ambient screen of every peer for `ttl_seconds` (default 60, clamped
5..3600). Good for "dinner is ready" pin-ups or "door was left open"
warnings.

Tap the banner on any speaker to dismiss; or wait for the TTL to auto-
clear.

## Start a timer on every speaker

> "Set a five-minute timer on every speaker"
> "全スピーカーで10分のタイマー"

Goes through `broadcast_timer`. Each receiver's local `TimerManager`
starts the timer, so the chime fires in every room simultaneously —
useful for cooking, kids, shared tasks.

## Cancel timers everywhere

> "Cancel timers on every speaker"
> "全スピーカーのタイマーを取り消し"

Goes through `broadcast_cancel_timer`. Clears every running timer on
every peer, not just this device's.

## Move a conversation to another speaker

> "Move this to the kitchen speaker"
> "キッチンにハンドオフ"

Goes through `handoff_session`. Sends the last N messages of the current
conversation to the target peer and clears local history there so the
target picks up exactly where this speaker left off. **Replace
semantics** — the target doesn't stack the new conversation on top of
its own unrelated chatter.

Media handoff is a planned follow-up; today the target keeps whatever
media it was already playing.

## Audit who's on the network

> "List nearby speakers"
> "近くのスピーカー"

Goes through `list_peers`. The agent reads out each peer's mDNS service
name and (when resolved) host:port. Good for checking a peer has come
online before trying to broadcast to it.

You can also open **Settings → System Info** to see the same list plus
the broadcasting name for this device.

## Group a subset of speakers

Go to **Settings → Multi-room → Speaker groups**. Create a group like
`kitchen`, add the mDNS service names of tablets you want in it, save.

Then:

> "Broadcast bath time to upstairs"

routes via the group instead of the whole mesh. Groups live only on the
device that created them — two tablets can legitimately disagree on
what each group means.

## Verify secrets match between speakers

Every speaker's Settings → Multi-room section shows a **4-word
fingerprint** derived from its secret. Read it out loud to the other
speaker's owner; if the phrases match word-for-word, the secrets match
byte-for-byte. Matches are deterministic — same secret on two devices
always produces the same 4 words from the bundled 256-word list.

## Reset traffic + rejection counters

Settings → System info shows a **Clear multi-room counters** button at
the bottom of the multi-room section when any counters are non-zero
(PR #290). Tapping it zeros both the `multiroom_traffic` and
`multiroom_rejections` Room tables in one go and refreshes the
snapshot. Useful when:

- Running a paired experiment (e.g. "reboot the mesh, watch rejections
  rise from zero") without having to wipe the whole database.
- Double-checking a clean handshake after rotating the shared secret —
  you want the first post-rotation envelope to be counted visibly.
- Showing someone else the live counters from a known-clean baseline.

The button hides itself when both tables are empty so a healthy mesh
doesn't show orphaned controls.

## Debugging

`adb logcat | grep -E "Envelope rejected|mDNS"` on each device shows:

| Log line | Meaning |
|---|---|
| `Envelope rejected: HMAC_MISMATCH` | Secrets differ; re-check pairing phrase |
| `Envelope rejected: REPLAY_WINDOW` | Clock skew > 30 s; enable automatic network time |
| `Envelope rejected: NO_SECRET` | Receiver's secret is blank; paste one in Settings |
| `Envelope rejected: VERSION_MISMATCH` | Protocol bumps; upgrade both apps |
| `mDNS discovery started for _opendash._tcp.` | Discovery is live |
| `mDNS registered as OpenDash-…` | This device is advertising |

## Tools summary

| Tool | Voice | Ships |
|---|---|---|
| `broadcast_tts` | "broadcast X to all/the {group}" | ✅ |
| `broadcast_announcement` | "announce X on every speaker for 5 minutes" | ✅ |
| `broadcast_timer` | "set a N minute timer on every speaker" | ✅ |
| `broadcast_cancel_timer` | "cancel timers on every speaker" | ✅ |
| `handoff_session` | "move this to the kitchen speaker" | ✅ conversation only |
| `list_peers` | "list nearby speakers" | ✅ |

For wire format + security model, see
[multi-room-protocol](multi-room-protocol.md).
