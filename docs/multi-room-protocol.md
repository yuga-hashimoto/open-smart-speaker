# Multi-room protocol (ADR)

Status: **Proposed** — scoping for Phase 17. No code yet. Captures the wire
format, security model, and message catalogue we will build on top of the
mDNS discovery layer shipped in P14.5.

Context: [`MulticastDiscovery.kt`](../app/src/main/java/com/opensmarthome/speaker/multiroom/MulticastDiscovery.kt)
already advertises the service type `_opensmartspeaker._tcp.` on
`DEFAULT_PORT = 8421`. Discovery works; no server actually listens on
that port yet. This document defines what the server, the client, and
the wire look like before we write any of them.

## Decision

We will run a **WebSocket server on TCP 8421** per speaker. Payloads are
**JSON envelopes**, one envelope per WebSocket text frame, with a
newline-terminated **NDJSON fallback** on the same port so that `nc` and
`curl --http1.1 --include` can be used for on-device debugging without a
WebSocket client. The fallback mode is chosen by sending an initial
`HELLO ndjson\n` line before any envelope; otherwise the server treats
the connection as a standard WebSocket upgrade (`GET /bus HTTP/1.1`
with `Upgrade: websocket`).

WebSocket was picked over raw TCP sockets because we get framing,
ping/pong liveness, and browser-compatible debug tooling "for free".
JSON was picked over protobuf/CBOR because the protocol is chatty at
human scale (a handful of messages per minute), schemas change during
development, and operators can read tcpdump output without tooling.

## Envelope schema

Every message — in either transport mode — is a JSON object with this
exact top-level shape. Unknown fields are ignored, missing required
fields cause the receiver to drop and log.

```json
{
  "v": 1,
  "type": "tts_broadcast",
  "id": "6f1b0a8a-1e2c-4a1c-9f4e-6b3a2c1d4e5f",
  "from": "speaker-living-room",
  "ts": 1713312000,
  "payload": { "text": "Dinner is ready", "language": "en" },
  "hmac": "b64(hmac_sha256(shared_secret, type|id|ts|payload))"
}
```

Field by field:

| Field | Type | Required | Meaning |
|---|---|---|---|
| `v` | integer | yes | Protocol version. Current: `1`. Mismatch → drop + warn. |
| `type` | string | yes | Message discriminator. See [Message types](#message-types-first-wave). |
| `id` | string (UUIDv4) | yes | Client-generated. Used for ack/correlate and replay detection. |
| `from` | string | yes | The sender's mDNS `serviceName` (e.g. `speaker-living-room`). Not a human label — matches exactly what `MulticastDiscovery` registered. |
| `ts` | integer | yes | Unix seconds at send time. Replay window: **30s** (see [Security](#security)). |
| `payload` | object | yes | Type-specific body. May be `{}` but never missing. |
| `hmac` | string | yes | Base64 HMAC-SHA256 of `type|id|ts|canonical_json(payload)` under the paired shared secret. |

`canonical_json` here means "UTF-8 encoded JSON with sorted object keys
and no insignificant whitespace" — same rules as JCS (RFC 8785). We
will use an existing Kotlin lib rather than rolling our own.

## Message types (first wave)

Shipped in P17.2 and P17.3. More will be added in later sub-items but
will go through their own ADR amendment.

### `tts_broadcast`

```json
{ "text": "Pizza is here", "language": "en" }
```

Every peer that receives this envelope speaks the text using its own TTS
provider. Opt-in per-device via a Settings toggle (off by default so a
new-on-LAN tablet can't silently push audio).

### `tts_target`

```json
{ "text": "Your laundry is done", "target": "speaker-kitchen" }
```

Same as `tts_broadcast` but only the peer whose `serviceName` matches
`target` speaks. All other peers drop silently. This is routed
client-side — the sender just picks who to send to — but `target` is
still echoed in the payload so the receiver can reject mis-routed
envelopes.

### `start_timer`

```json
{ "seconds": 300, "label": "pasta", "scope": "all" }
```

`scope` is `all` (every peer sets its own copy of the timer) or `this`
(only the receiver — used by `tts_target`-style one-shot routing).
`label` is optional.

### `cancel_timer`

```json
{ "id": "6f1b0a8a-..." }
```

Or:

```json
{ "id": "all" }
```

The special id `"all"` cancels every active timer on every receiving
peer, matching the existing `cancel_all_timers` fast-path tool.

### `announcement`

```json
{ "message": "Package delivered", "ttl_seconds": 900 }
```

Behaves like `tts_broadcast` but also persists on the ambient screen
for `ttl_seconds`, then expires. Persistence is per-device — the sender
does not track it. Useful for "you have a package", "guest arriving at
7pm", etc.

### `heartbeat`

```json
{}
```

Liveness probe. Response is another `heartbeat` envelope with a fresh
`id`. Clients fall back to WebSocket-level ping/pong when available;
this message exists for NDJSON-mode debugging.

### `error`

```json
{ "reason": "hmac_mismatch" }
```

Generic failure response. The `id` field must equal the `id` of the
envelope being rejected, so senders can correlate. Receivers only send
`error` back when the envelope was *otherwise* valid (well-formed,
in-window, trusted peer) — silent drops are preferred for attacks.

## Security

### Decision: QR-paired shared secret + per-envelope HMAC

At first-time setup, one speaker generates a 256-bit secret, displays
it as a QR code, and the peer scans it. The secret is stored in
`SecurePreferences` on both ends and never transmitted over the
network. Every envelope carries an `hmac` field computed as:

```
hmac_sha256(shared_secret, type || "|" || id || "|" || ts || "|" || canonical_json(payload))
```

Separators are literal `|` bytes. The receiver recomputes and
constant-time compares. Mismatch → drop + log + no retry, no `error`
response (we don't want to help an attacker probe secrets).

**Replay window**: reject envelopes where `abs(now - ts) > 30` seconds.
Clock drift beyond 30s is a real problem on tablets anyway; this
catches both replays and unset clocks. We additionally remember the
last 256 seen `(from, id)` pairs per peer and drop duplicates inside
the window.

### Alternatives considered for auth

- **Trusted LAN**: rejected. Guest Wi-Fi, hostile roommates, smart
  TVs that probe every open port — LAN is not a trust boundary.
- **TLS with self-signed certs + TOFU**: rejected for v1. Useful later
  but adds a cert-rotation UX problem we don't want to solve yet.
  Nothing in the envelope is secret (text messages are meant to be
  spoken out loud), so confidentiality-at-rest isn't required; we
  only need integrity + authenticity, which HMAC provides.
- **QR-paired shared secret** (chosen): one-time UX cost, zero-config
  steady state, survives device reboots. Natural fit for a tablet
  with a screen and a camera already.

## Group semantics

Speaker groups are **user-named subsets of discovered peers** stored
in each device's local prefs only. Example: a group `"Upstairs"`
containing `["speaker-bedroom", "speaker-office"]`.

Groups are **not** a protocol concept. Routing is always "send this
envelope to every peer whose `serviceName` is in my local group X",
performed client-side by opening one WebSocket per target and sending
the same envelope to each. This keeps the receiver dumb — it never
needs to know what group it belongs to, which means group membership
changes on one device don't require broadcasting a manifest.

## Failure modes

- **Peer goes offline**: `MulticastDiscovery.onServiceLost` fires, the
  cached client drops the WebSocket on next send or on ping timeout
  (~15s). No retry; callers see `SendResult.PeerOffline` and can
  choose what to do (most tools just log).
- **Peer HMAC fails**: receiver drops envelope, logs `hmac_mismatch`
  with `from` and `id`, does not send `error` back. No retry.
- **Replay window miss** (`abs(now - ts) > 30`): receiver drops, logs
  `stale_envelope` with the delta. No `error` response.
- **Duplicate `(from, id)` inside window**: drop, log `replay`.
- **Schema `v` mismatch**: receiver drops, logs `version_mismatch`,
  and surfaces a one-shot UI warning like "peer X is speaking a newer
  protocol — update this speaker". Sender gets no response.
- **Malformed JSON / missing required field**: drop + log. The
  connection is kept open because the next frame might be valid.

## Port rationale

`8421` is unassigned by IANA, unused by every common dev-server we run
into (`:8080`, `:8443`, `:5000`, `:3000`), and not colliding with
neighbouring smart-home stacks (`:8123` Home Assistant, `:1883` MQTT,
`:1400` Sonos, `:8009` Chromecast). mDNS advertises the port already,
so even if we move it later nobody hard-codes it.

mDNS TXT records **may** include `{"v": 1}` so that incompatible
protocol versions can be filtered at discovery time, but this is
optional — an omitted TXT record is treated as "try v1, be ready to
drop with `version_mismatch` on first envelope".

## Alternatives considered (transport)

- **gRPC / HTTP2**: rejected. Code-gen weight, runtime size, and the
  need for `.proto` files stored somewhere make debugging harder.
  There is no browser gRPC client worth depending on, and we value
  `nc localhost 8421` as a first-class debug tool.
- **MQTT**: rejected. Requires a broker (either embedded per device or
  a shared one), which defeats the LAN-first, zero-infra story. Good
  protocol, wrong deployment shape for tablets.
- **Raw UDP multicast**: rejected. No ordering, no ack, no auth
  framing, and a single dropped packet in the middle of an
  announcement is worse than a retry.
- **Plain HTTP + long-poll**: rejected. Half the complexity of
  WebSocket with none of the debuggability wins, and bidirectional
  liveness needs a second connection per peer.

## Phase 17 sub-items that depend on this ADR

Copied verbatim from [`roadmap.md`](roadmap.md) so future amendments
stay aligned.

- **P17.2**: `AnnouncementServer` + `AnnouncementClient` — listens on
  DEFAULT_PORT; when a peer sends `{"type":"tts_broadcast", "text":"..."}`
  it speaks on this device. Opt-in via new Settings toggle.
- **P17.3**: Timer sync — when user says "set a timer on all speakers
  5 minutes", fan out to discovered peers with
  `{"type":"start_timer", "seconds":300}`.
- **P17.4**: Speaker groups — user-named subsets of discovered peers
  for room-level routing.
- **P17.5**: Session handoff — "move this to the kitchen speaker"
  transfers the active media device + conversation context via the
  bus.
- **P17.6**: Authentication — shared-secret QR pairing between peers;
  reject unpaired message senders (protects against random LAN
  guests).

## Out of scope

- **Audio-stream mirroring** — pushing raw PCM or an Opus stream from
  one speaker to another (DLNA / AirPlay territory). The protocol
  carries *text to speak*, not *audio to play*; synchronised
  multi-room playback is a separate engineering problem and will get
  its own ADR if we pursue it.
- **Device re-auth after phone swap** — if the user replaces a
  tablet, they re-pair via QR. We will not implement a recovery flow
  that lets a new device claim an old speaker's identity, because
  that is exactly the attack we're defending against.
- **Cross-LAN routing** — no NAT traversal, no relay server, no
  Tailscale integration. All peers must share a broadcast domain.
  Users who want remote access should use Home Assistant's existing
  remote paths.
- **Schema negotiation** — we will not build a capability-exchange
  handshake. Version bumps go through this ADR and a coordinated
  release. If that becomes painful we'll revisit in v2.
