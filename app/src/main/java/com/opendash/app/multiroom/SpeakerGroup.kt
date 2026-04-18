package com.opendash.app.multiroom

/**
 * A named, client-side subset of the mDNS-discovered peer list.
 *
 * Per ADR (docs/multi-room-protocol.md §Group semantics), groups are
 * **not** a protocol concept — they exist only in the broadcaster's local
 * prefs. Two speakers can legitimately have disjoint group maps, and
 * receivers never see a `group` field on the wire.
 *
 * [memberServiceNames] stores mDNS `serviceName` strings (the same
 * identifier the broadcaster matches against `DiscoveredSpeaker.serviceName`
 * at send time). Stored as a Set because order doesn't matter and
 * duplicates are nonsensical.
 */
data class SpeakerGroup(
    val name: String,
    val memberServiceNames: Set<String>
)
