---
title: Multi-room FAQ
---

# Multi-room FAQ

Answers to the questions new users ask about pairing two or more
OpenSmartSpeaker tablets for broadcasts, announcements, and session
handoff. If you haven't paired devices yet, start with the
[Multi-room quickstart](multi-room-quickstart.md). For tool-by-tool
recipes see the [Multi-room cookbook](multi-room-cookbook.md). For the
wire format and security rationale see the
[Multi-room protocol](multi-room-protocol.md).

## Setup and networking

### Q: Does my router need any special configuration?

No. Multi-room runs over a plain IPv4 LAN using Android's standard mDNS
(`_opensmartspeaker._tcp.` on port 8421) and a WebSocket/NDJSON bus on
the same port. You don't need port forwarding, static DHCP reservations,
or custom firewall rules. The only thing that reliably breaks discovery
is "AP isolation" / "guest network isolation" on the router, which stops
clients on the same SSID from seeing each other — turn that off for the
SSID your tablets use. See the quickstart's
[Common gotchas table](multi-room-quickstart.md#common-gotchas) for
related symptoms.

### Q: Does this work over WAN or through a VPN?

No. The protocol is LAN-scoped by design. Peer discovery uses mDNS,
which only propagates inside a single broadcast domain — it does not
cross routers, does not traverse the public internet, and a VPN tunnel
typically delivers unicast traffic only, not the multicast that mDNS
depends on. If you want to reach your speakers from outside your home
network, use Home Assistant's existing remote-access paths instead.
Cross-LAN routing is explicitly [out of scope](multi-room-protocol.md#out-of-scope).

### Q: How do I pair a new speaker?

Install the same APK, turn on **Settings → Multi-room broadcast**,
then paste the **same** shared secret you configured on your existing
tablets into **Settings → Multi-room shared secret**. The Pairing phrase
box on every tablet should show the same four words — that's your
byte-for-byte match check. There are no further steps; the new speaker
will show up in "List nearby speakers" within about three seconds. The
full flow is in the [quickstart](multi-room-quickstart.md#step-2--set-a-shared-secret).

### Q: What happens if my clock drifts?

The receiver rejects any envelope whose timestamp is more than **30
seconds** off from its local clock (`Envelope rejected: REPLAY_WINDOW`
in logcat). This single check catches both replay attacks and tablets
with a broken clock. The fix is to enable automatic network time on
every tablet: **Settings → System → Date & time → Set time
automatically**. After that, clock drift between two tablets on the
same LAN is essentially zero.

## Privacy and security

### Q: Is my audio being streamed to other speakers?

No. **No audio ever crosses the wire.** When you say "broadcast dinner
is ready to all speakers", the sender transmits a short JSON envelope
containing the *text* `"dinner is ready"`. Each receiver then
re-synthesises that text locally using its own on-device TTS. This
means every speaker picks its own voice, volume, and language settings,
there is no raw PCM or Opus stream hitting the network, and a packet
capture will never contain sampled audio of your conversations.
Audio-stream mirroring (DLNA/AirPlay-style synchronised playback) is
explicitly [out of scope for v1](multi-room-protocol.md#out-of-scope).

### Q: What data actually crosses the wire?

Short JSON envelopes, one per WebSocket frame (or one per line in
NDJSON fallback mode), containing only these fields:

- `v` — protocol version (`1`)
- `type` — message discriminator (`tts_broadcast`, `start_timer`, etc.)
- `id` — UUID for dedup/correlate
- `from` — sender's mDNS service name
- `ts` — unix timestamp (used for the replay window)
- `payload` — the type-specific body (e.g. `{"text": "dinner is ready", "language": "en"}`)
- `hmac` — base64 HMAC-SHA256 signature over the above

Nothing else — no auth tokens, no audio, no conversation history unless
you specifically use session handoff. Full schema in the
[envelope reference](multi-room-protocol.md#envelope-schema).

### Q: Why HMAC instead of TLS?

Because the threats we care about are **integrity and authenticity**
(nobody else on the LAN should be able to forge a broadcast that your
speakers obey), not confidentiality. The payloads are text that the
receiver is about to speak out loud across the room — if a passive
eavesdropper learns that dinner is ready, no meaningful secret has
leaked. HMAC gives us what we need with a one-time paired secret and
no certificate-rotation UX to build. TLS with self-signed certs + TOFU
was considered and rejected for v1; see the
[auth alternatives discussion](multi-room-protocol.md#alternatives-considered-for-auth)
for the full trade-off.

### Q: Can guests on my Wi-Fi see my announcements?

A guest on your LAN can **see** the envelopes (they're plaintext JSON
on TCP 8421), so yes, they can observe that you broadcast "dinner is
ready". What they cannot do is **forge** one, because every envelope
carries an HMAC-SHA256 signature keyed by the shared secret, and
receivers drop anything with a bad signature. Verify your pairing is
correct by checking the 4-word Pairing phrase on each tablet — same
phrase means same secret. If you care about hiding even the text of
announcements from co-LAN observers, put the tablets on a dedicated
VLAN; the protocol deliberately does not add TLS at the v1 stage.

### Q: Will broadcasts wake a locked speaker?

It depends on the OS power state of the receiver. A plugged-in tablet
mounted as a smart display with "always on" mode will show the
announcement banner and speak the TTS immediately. A tablet that is
fully locked and asleep on battery may defer or miss the broadcast —
the Android system holds the mic/TTS process but can throttle
foreground-service wakeups aggressively, especially under doze. For
reliable reception, keep receivers plugged in and in ambient-display
mode. This is an OS-level behaviour, not a protocol choice.

## Scope and interop

### Q: Why do speaker groups stay client-side?

Groups like `kitchen` or `upstairs` live **only on the device that
created them**. The sender picks the list of peers locally and opens
one WebSocket per target. This is deliberately simpler than negotiating
group membership across the mesh: no manifest broadcast, no
leader-election when a device leaves, no split-brain when two tablets
edit the same group name. Two tablets can legitimately disagree on what
"kitchen" means and nothing breaks. See the
[Group semantics section](multi-room-protocol.md#group-semantics) of
the ADR for the full argument.

### Q: What about video?

Out of scope. The multi-room bus only routes **text-to-speak, timer
state, announcements, and small conversation payloads**. There is no
video call primitive, no screen-share, no camera-stream forwarding.
The envelope size is practically bounded (v1 caps WebSocket text
frames at 65 535 bytes) and the dispatcher will not accept
`type` values outside the [message catalogue](multi-room-protocol.md#message-types-first-wave).
If you need video, use a different tool.

### Q: Can this replace Alexa's "drop-in"?

Kind of — but only the one-way direction. `broadcast_tts` lets you
push a spoken message to every paired tablet, which covers the
"announce to the kitchen" use case. It does **not** establish a
two-way audio call; there's no microphone stream going back, and the
receiver won't hear you after the initial message is spoken. A real
drop-in replacement would need a WebRTC stack for bidirectional audio,
which isn't part of the current protocol or roadmap. If you want the
audio-call behaviour specifically, that's a separate feature and isn't
planned for v1.

### Q: Can I use this from a non-Android device?

Technically yes, because the protocol is open and documented. The wire
format is JSON envelopes over WebSocket (or NDJSON over raw TCP) on
port 8421, signed with HMAC-SHA256 over a shared secret. You can
reimplement the sender in any language — port the Kotlin broadcaster
or hand-craft signed envelopes and feed them to the port. You can also
`nc` into the NDJSON fallback for debugging, which is why we kept it.
That said, this isn't a supported path: we only ship and test the
Kotlin/Android client, and schema changes go through the ADR with no
backwards-compat promise to external implementers. Use at your own
risk.

### Q: Does session handoff work across different Android OS versions?

It should, because the envelope shape is versioned (`"v": 1`) and the
handoff tool only requires that both the sender and the receiver are
running an app build that shipped `handoff_session` (i.e. the P17.5
release or later). The protocol doesn't care what Android version sits
underneath — if the app installs and the foreground service runs, the
bus works. If you see handoff silently no-op on one end, first confirm
both apps are on a build that includes the tool (check the cookbook's
[Tools summary](multi-room-cookbook.md#tools-summary)), and then check
logcat for `VERSION_MISMATCH` in case the protocol gets bumped to v2 in
a future release.
