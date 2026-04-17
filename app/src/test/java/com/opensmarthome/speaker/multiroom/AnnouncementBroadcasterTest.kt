package com.opensmarthome.speaker.multiroom

import com.google.common.truth.Truth.assertThat
import com.opensmarthome.speaker.assistant.model.AssistantMessage
import com.opensmarthome.speaker.data.preferences.SecurePreferences
import com.opensmarthome.speaker.util.DiscoveredSpeaker
import com.opensmarthome.speaker.util.MulticastDiscovery
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class AnnouncementBroadcasterTest {

    private val moshi = Moshi.Builder().build()
    private val mapAdapter: JsonAdapter<Map<String, Any?>> = moshi.adapter(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    )

    private val secret = "shared-secret"

    private fun securePrefs(secret: String?): SecurePreferences {
        val prefs = mockk<SecurePreferences>(relaxed = true)
        every { prefs.getString(SecurePreferences.KEY_MULTIROOM_SECRET) } returns (secret ?: "")
        every { prefs.getString(SecurePreferences.KEY_MULTIROOM_SECRET, any()) } returns (secret ?: "")
        return prefs
    }

    private fun discovery(peers: List<DiscoveredSpeaker>, self: String? = "speaker-self"): Pair<MulticastDiscovery, () -> String?> {
        val d = mockk<MulticastDiscovery>(relaxed = true)
        val flow = MutableStateFlow(peers)
        every { d.speakers } returns flow.asStateFlow()
        val selfFlow = MutableStateFlow(self)
        every { d.registeredName } returns selfFlow.asStateFlow()
        return d to { selfFlow.value }
    }

    @Test
    fun `broadcastTts builds signed envelope and fans out to every resolved peer`() = runTest {
        val peers = listOf(
            DiscoveredSpeaker("speaker-kitchen", host = "10.0.0.2", port = 8421),
            DiscoveredSpeaker("speaker-bedroom", host = "10.0.0.3", port = 8421)
        )
        val (d, self) = discovery(peers)
        val client = mockk<AnnouncementClient>()
        val lineSlot = slot<String>()
        coEvery { client.send(any(), any(), capture(lineSlot), any()) } returns SendOutcome.Ok

        val broadcaster = AnnouncementBroadcaster(
            discovery = d,
            client = client,
            securePreferences = securePrefs(secret),
            moshi = moshi,
            selfServiceName = self,
            clock = { 1_000L },
            idGenerator = { "id-42" }
        )

        val result = broadcaster.broadcastTts("pizza is here", "en")

        assertThat(result.sentCount).isEqualTo(2)
        assertThat(result.failures).isEmpty()
        coVerify(exactly = 1) { client.send("10.0.0.2", 8421, any(), any()) }
        coVerify(exactly = 1) { client.send("10.0.0.3", 8421, any(), any()) }

        // Envelope schema + HMAC sanity check: parse the captured line and verify
        // every required field + that the signature round-trips under `secret`.
        @Suppress("UNCHECKED_CAST")
        val envelope = mapAdapter.fromJson(lineSlot.captured) as Map<String, Any?>
        assertThat(envelope["v"]).isEqualTo(1.0)  // Moshi decodes number as Double
        assertThat(envelope["type"]).isEqualTo(AnnouncementType.TTS_BROADCAST)
        assertThat(envelope["id"]).isEqualTo("id-42")
        assertThat(envelope["from"]).isEqualTo("speaker-self")
        assertThat((envelope["ts"] as Number).toLong()).isEqualTo(1_000L)
        @Suppress("UNCHECKED_CAST")
        val payload = envelope["payload"] as Map<String, Any?>
        assertThat(payload["text"]).isEqualTo("pizza is here")
        assertThat(payload["language"]).isEqualTo("en")

        // HMAC must verify under the same secret the broadcaster used.
        val payloadJson = mapAdapter.toJson(payload)
        assertThat(
            HmacSigner.verify(
                secret = secret,
                type = AnnouncementType.TTS_BROADCAST,
                id = "id-42",
                ts = 1_000L,
                payloadJson = payloadJson,
                expected = envelope["hmac"] as String
            )
        ).isTrue()
    }

    @Test
    fun `broadcastTts returns failures for peers whose send fails`() = runTest {
        val peers = listOf(
            DiscoveredSpeaker("ok-peer", host = "10.0.0.2", port = 8421),
            DiscoveredSpeaker("down-peer", host = "10.0.0.3", port = 8421)
        )
        val (d, self) = discovery(peers)
        val client = mockk<AnnouncementClient>()
        coEvery { client.send("10.0.0.2", 8421, any(), any()) } returns SendOutcome.Ok
        coEvery { client.send("10.0.0.3", 8421, any(), any()) } returns SendOutcome.ConnectionRefused

        val broadcaster = AnnouncementBroadcaster(
            discovery = d,
            client = client,
            securePreferences = securePrefs(secret),
            moshi = moshi,
            selfServiceName = self
        )

        val result = broadcaster.broadcastTts("hello")
        assertThat(result.sentCount).isEqualTo(1)
        assertThat(result.failures).containsExactly("down-peer" to SendOutcome.ConnectionRefused)
    }

    @Test
    fun `broadcastTts skips peers with unresolved host or null port`() = runTest {
        val peers = listOf(
            DiscoveredSpeaker("unresolved", host = null, port = null),
            DiscoveredSpeaker("half-resolved", host = "10.0.0.5", port = null),
            DiscoveredSpeaker("good", host = "10.0.0.6", port = 8421)
        )
        val (d, self) = discovery(peers)
        val client = mockk<AnnouncementClient>()
        coEvery { client.send(any(), any(), any(), any()) } returns SendOutcome.Ok

        val broadcaster = AnnouncementBroadcaster(
            discovery = d,
            client = client,
            securePreferences = securePrefs(secret),
            moshi = moshi,
            selfServiceName = self
        )

        val result = broadcaster.broadcastTts("hi")
        assertThat(result.sentCount).isEqualTo(1)
        coVerify(exactly = 1) { client.send("10.0.0.6", 8421, any(), any()) }
    }

    @Test
    fun `missing secret short-circuits without sending anything`() = runTest {
        val peers = listOf(DiscoveredSpeaker("k", host = "10.0.0.2", port = 8421))
        val (d, self) = discovery(peers)
        val client = mockk<AnnouncementClient>()

        val broadcaster = AnnouncementBroadcaster(
            discovery = d,
            client = client,
            securePreferences = securePrefs(null),
            moshi = moshi,
            selfServiceName = self
        )

        val result = broadcaster.broadcastTts("silent")
        assertThat(result.sentCount).isEqualTo(0)
        assertThat(result.failures)
            .containsExactly("none" to SendOutcome.Other("no shared secret"))
        coVerify(exactly = 0) { client.send(any(), any(), any(), any()) }
    }

    @Test
    fun `broadcastTtsToGroup only sends to members that are also discovered`() = runTest {
        val peers = listOf(
            DiscoveredSpeaker("speaker-kitchen", host = "10.0.0.2", port = 8421),
            DiscoveredSpeaker("speaker-bedroom", host = "10.0.0.3", port = 8421),
            DiscoveredSpeaker("speaker-office", host = "10.0.0.4", port = 8421)
        )
        val (d, self) = discovery(peers)
        val client = mockk<AnnouncementClient>()
        coEvery { client.send(any(), any(), any(), any()) } returns SendOutcome.Ok

        val group = SpeakerGroup(
            name = "kitchen",
            // includes one discovered peer + one that hasn't been seen
            memberServiceNames = setOf("speaker-kitchen", "speaker-offline")
        )

        val broadcaster = AnnouncementBroadcaster(
            discovery = d,
            client = client,
            securePreferences = securePrefs(secret),
            moshi = moshi,
            selfServiceName = self,
            groupLookup = { name -> if (name == "kitchen") group else null }
        )

        val result = broadcaster.broadcastTtsToGroup("kitchen", "pizza", "en")

        assertThat(result.sentCount).isEqualTo(1)
        assertThat(result.failures).isEmpty()
        coVerify(exactly = 1) { client.send("10.0.0.2", 8421, any(), any()) }
        // Non-members must not receive a send.
        coVerify(exactly = 0) { client.send("10.0.0.3", 8421, any(), any()) }
        coVerify(exactly = 0) { client.send("10.0.0.4", 8421, any(), any()) }
    }

    @Test
    fun `broadcastTtsToGroup returns unknown group failure when not persisted`() = runTest {
        val peers = listOf(DiscoveredSpeaker("k", host = "10.0.0.2", port = 8421))
        val (d, self) = discovery(peers)
        val client = mockk<AnnouncementClient>()

        val broadcaster = AnnouncementBroadcaster(
            discovery = d,
            client = client,
            securePreferences = securePrefs(secret),
            moshi = moshi,
            selfServiceName = self,
            groupLookup = { null }
        )

        val result = broadcaster.broadcastTtsToGroup("unknown", "hi", "en")
        assertThat(result.sentCount).isEqualTo(0)
        assertThat(result.failures)
            .containsExactly("missing" to SendOutcome.Other("unknown group: unknown"))
        coVerify(exactly = 0) { client.send(any(), any(), any(), any()) }
    }

    @Test
    fun `broadcastTtsToGroup still short-circuits when secret missing`() = runTest {
        val peers = listOf(DiscoveredSpeaker("k", host = "10.0.0.2", port = 8421))
        val (d, self) = discovery(peers)
        val client = mockk<AnnouncementClient>()
        val group = SpeakerGroup("kitchen", setOf("k"))

        val broadcaster = AnnouncementBroadcaster(
            discovery = d,
            client = client,
            securePreferences = securePrefs(null),
            moshi = moshi,
            selfServiceName = self,
            groupLookup = { if (it == "kitchen") group else null }
        )

        val result = broadcaster.broadcastTtsToGroup("kitchen", "hi", "en")
        assertThat(result.sentCount).isEqualTo(0)
        assertThat(result.failures)
            .containsExactly("none" to SendOutcome.Other("no shared secret"))
        coVerify(exactly = 0) { client.send(any(), any(), any(), any()) }
    }

    @Test
    fun `broadcastTtsToGroup with no reachable members reports zero without failures`() = runTest {
        val peers = listOf(DiscoveredSpeaker("speaker-bedroom", host = "10.0.0.3", port = 8421))
        val (d, self) = discovery(peers)
        val client = mockk<AnnouncementClient>()
        val group = SpeakerGroup("kitchen", setOf("speaker-kitchen"))

        val broadcaster = AnnouncementBroadcaster(
            discovery = d,
            client = client,
            securePreferences = securePrefs(secret),
            moshi = moshi,
            selfServiceName = self,
            groupLookup = { if (it == "kitchen") group else null }
        )

        val result = broadcaster.broadcastTtsToGroup("kitchen", "anyone?", "en")
        assertThat(result.sentCount).isEqualTo(0)
        assertThat(result.failures).isEmpty()
        coVerify(exactly = 0) { client.send(any(), any(), any(), any()) }
    }

    @Test
    fun `broadcastTts falls back to default from when registered name is null`() = runTest {
        val peers = listOf(DiscoveredSpeaker("k", host = "10.0.0.2", port = 8421))
        val (d, self) = discovery(peers, self = null)
        val client = mockk<AnnouncementClient>()
        val lineSlot = slot<String>()
        coEvery { client.send(any(), any(), capture(lineSlot), any()) } returns SendOutcome.Ok

        val broadcaster = AnnouncementBroadcaster(
            discovery = d,
            client = client,
            securePreferences = securePrefs(secret),
            moshi = moshi,
            selfServiceName = self
        )

        broadcaster.broadcastTts("hello")
        @Suppress("UNCHECKED_CAST")
        val envelope = mapAdapter.fromJson(lineSlot.captured) as Map<String, Any?>
        assertThat(envelope["from"]).isEqualTo(AnnouncementBroadcaster.DEFAULT_FROM)
    }

    @Test
    fun `handoffConversation sends one envelope to matching peer with conversation payload`() = runTest {
        val peers = listOf(
            DiscoveredSpeaker("speaker-kitchen", host = "10.0.0.2", port = 8421),
            DiscoveredSpeaker("speaker-bedroom", host = "10.0.0.3", port = 8421)
        )
        val (d, self) = discovery(peers)
        val client = mockk<AnnouncementClient>()
        val lineSlot = slot<String>()
        coEvery { client.send("10.0.0.2", 8421, capture(lineSlot), any()) } returns SendOutcome.Ok

        val broadcaster = AnnouncementBroadcaster(
            discovery = d,
            client = client,
            securePreferences = securePrefs(secret),
            moshi = moshi,
            selfServiceName = self,
            clock = { 2_000L },
            idGenerator = { "id-handoff" }
        )

        // Friendly alias "kitchen" should resolve to "speaker-kitchen"
        val outcome = broadcaster.handoffConversation(
            targetServiceName = "kitchen",
            messages = listOf(
                AssistantMessage.User(content = "what's the weather"),
                AssistantMessage.Assistant(content = "Sunny and 22."),
                AssistantMessage.ToolCallResult(callId = "c", result = "drop me"),
                AssistantMessage.Delta(contentDelta = "drop me too")
            )
        )

        assertThat(outcome).isEqualTo(SendOutcome.Ok)
        coVerify(exactly = 1) { client.send("10.0.0.2", 8421, any(), any()) }
        coVerify(exactly = 0) { client.send("10.0.0.3", 8421, any(), any()) }

        @Suppress("UNCHECKED_CAST")
        val envelope = mapAdapter.fromJson(lineSlot.captured) as Map<String, Any?>
        assertThat(envelope["type"]).isEqualTo(AnnouncementType.SESSION_HANDOFF)
        @Suppress("UNCHECKED_CAST")
        val payload = envelope["payload"] as Map<String, Any?>
        assertThat(payload["mode"]).isEqualTo(AnnouncementBroadcaster.MODE_CONVERSATION)
        @Suppress("UNCHECKED_CAST")
        val msgs = payload["messages"] as List<Map<String, Any?>>
        // Tool results and deltas must be filtered out before transmission.
        assertThat(msgs).hasSize(2)
        assertThat(msgs[0]["role"]).isEqualTo("user")
        assertThat(msgs[0]["content"]).isEqualTo("what's the weather")
        assertThat(msgs[1]["role"]).isEqualTo("assistant")
        assertThat(msgs[1]["content"]).isEqualTo("Sunny and 22.")

        // HMAC must verify under the broadcaster's secret.
        val payloadJson = mapAdapter.toJson(payload)
        assertThat(
            HmacSigner.verify(
                secret = secret,
                type = AnnouncementType.SESSION_HANDOFF,
                id = "id-handoff",
                ts = 2_000L,
                payloadJson = payloadJson,
                expected = envelope["hmac"] as String
            )
        ).isTrue()
    }

    @Test
    fun `handoffConversation returns failure when target does not match any peer`() = runTest {
        val peers = listOf(DiscoveredSpeaker("speaker-kitchen", host = "10.0.0.2", port = 8421))
        val (d, self) = discovery(peers)
        val client = mockk<AnnouncementClient>()

        val broadcaster = AnnouncementBroadcaster(
            discovery = d,
            client = client,
            securePreferences = securePrefs(secret),
            moshi = moshi,
            selfServiceName = self
        )

        val outcome = broadcaster.handoffConversation(
            targetServiceName = "garage",
            messages = listOf(AssistantMessage.User(content = "hi"))
        )
        assertThat(outcome).isInstanceOf(SendOutcome.Other::class.java)
        assertThat((outcome as SendOutcome.Other).reason).contains("garage")
        coVerify(exactly = 0) { client.send(any(), any(), any(), any()) }
    }

    @Test
    fun `broadcastAnnouncement builds signed announcement envelope with ttl_seconds`() = runTest {
        val peers = listOf(
            DiscoveredSpeaker("speaker-kitchen", host = "10.0.0.2", port = 8421),
            DiscoveredSpeaker("speaker-bedroom", host = "10.0.0.3", port = 8421)
        )
        val (d, self) = discovery(peers)
        val client = mockk<AnnouncementClient>()
        val lineSlot = slot<String>()
        coEvery { client.send(any(), any(), capture(lineSlot), any()) } returns SendOutcome.Ok

        val broadcaster = AnnouncementBroadcaster(
            discovery = d,
            client = client,
            securePreferences = securePrefs(secret),
            moshi = moshi,
            selfServiceName = self,
            clock = { 3_000L },
            idGenerator = { "id-announce" }
        )

        val result = broadcaster.broadcastAnnouncement(text = "dinner ready", ttlSeconds = 90)
        assertThat(result.sentCount).isEqualTo(2)
        assertThat(result.failures).isEmpty()

        @Suppress("UNCHECKED_CAST")
        val envelope = mapAdapter.fromJson(lineSlot.captured) as Map<String, Any?>
        assertThat(envelope["type"]).isEqualTo(AnnouncementType.ANNOUNCEMENT)
        @Suppress("UNCHECKED_CAST")
        val payload = envelope["payload"] as Map<String, Any?>
        assertThat(payload["text"]).isEqualTo("dinner ready")
        assertThat((payload["ttl_seconds"] as Number).toInt()).isEqualTo(90)

        // Re-serialize payload for HMAC verify — Moshi decoded ttl_seconds
        // as Double, so we normalise back to Int to match the JSON the
        // broadcaster signed over (otherwise "90.0" vs "90" diverges).
        val normalised: Map<String, Any?> = mapOf(
            "text" to payload["text"],
            "ttl_seconds" to (payload["ttl_seconds"] as Number).toInt()
        )
        val payloadJson = mapAdapter.toJson(normalised)
        assertThat(
            HmacSigner.verify(
                secret = secret,
                type = AnnouncementType.ANNOUNCEMENT,
                id = "id-announce",
                ts = 3_000L,
                payloadJson = payloadJson,
                expected = envelope["hmac"] as String
            )
        ).isTrue()
    }

    @Test
    fun `broadcastAnnouncement clamps ttl below the minimum`() = runTest {
        val peers = listOf(DiscoveredSpeaker("k", host = "10.0.0.2", port = 8421))
        val (d, self) = discovery(peers)
        val client = mockk<AnnouncementClient>()
        val lineSlot = slot<String>()
        coEvery { client.send(any(), any(), capture(lineSlot), any()) } returns SendOutcome.Ok

        val broadcaster = AnnouncementBroadcaster(
            discovery = d, client = client,
            securePreferences = securePrefs(secret), moshi = moshi, selfServiceName = self
        )

        broadcaster.broadcastAnnouncement(text = "hi", ttlSeconds = 1)
        @Suppress("UNCHECKED_CAST")
        val envelope = mapAdapter.fromJson(lineSlot.captured) as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val payload = envelope["payload"] as Map<String, Any?>
        assertThat((payload["ttl_seconds"] as Number).toInt())
            .isEqualTo(AnnouncementDispatcher.TTL_MIN_SECONDS)
    }

    @Test
    fun `broadcastAnnouncement clamps ttl above the maximum`() = runTest {
        val peers = listOf(DiscoveredSpeaker("k", host = "10.0.0.2", port = 8421))
        val (d, self) = discovery(peers)
        val client = mockk<AnnouncementClient>()
        val lineSlot = slot<String>()
        coEvery { client.send(any(), any(), capture(lineSlot), any()) } returns SendOutcome.Ok

        val broadcaster = AnnouncementBroadcaster(
            discovery = d, client = client,
            securePreferences = securePrefs(secret), moshi = moshi, selfServiceName = self
        )

        broadcaster.broadcastAnnouncement(text = "hi", ttlSeconds = 99_999)
        @Suppress("UNCHECKED_CAST")
        val envelope = mapAdapter.fromJson(lineSlot.captured) as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val payload = envelope["payload"] as Map<String, Any?>
        assertThat((payload["ttl_seconds"] as Number).toInt())
            .isEqualTo(AnnouncementDispatcher.TTL_MAX_SECONDS)
    }

    @Test
    fun `broadcastAnnouncement returns missing-secret failure without sending`() = runTest {
        val peers = listOf(DiscoveredSpeaker("k", host = "10.0.0.2", port = 8421))
        val (d, self) = discovery(peers)
        val client = mockk<AnnouncementClient>()

        val broadcaster = AnnouncementBroadcaster(
            discovery = d, client = client,
            securePreferences = securePrefs(null), moshi = moshi, selfServiceName = self
        )

        val result = broadcaster.broadcastAnnouncement("silent", 60)
        assertThat(result.sentCount).isEqualTo(0)
        assertThat(result.failures)
            .containsExactly("none" to SendOutcome.Other("no shared secret"))
        coVerify(exactly = 0) { client.send(any(), any(), any(), any()) }
    }

    @Test
    fun `broadcastTimer builds signed start_timer envelope and fans out`() = runTest {
        val peers = listOf(
            DiscoveredSpeaker("speaker-kitchen", host = "10.0.0.2", port = 8421),
            DiscoveredSpeaker("speaker-bedroom", host = "10.0.0.3", port = 8421)
        )
        val (d, self) = discovery(peers)
        val client = mockk<AnnouncementClient>()
        val lineSlot = slot<String>()
        coEvery { client.send(any(), any(), capture(lineSlot), any()) } returns SendOutcome.Ok

        val broadcaster = AnnouncementBroadcaster(
            discovery = d,
            client = client,
            securePreferences = securePrefs(secret),
            moshi = moshi,
            selfServiceName = self,
            clock = { 3_000L },
            idGenerator = { "id-timer" }
        )

        val result = broadcaster.broadcastTimer(seconds = 300, label = "tea")

        assertThat(result.sentCount).isEqualTo(2)
        assertThat(result.failures).isEmpty()
        coVerify(exactly = 1) { client.send("10.0.0.2", 8421, any(), any()) }
        coVerify(exactly = 1) { client.send("10.0.0.3", 8421, any(), any()) }

        @Suppress("UNCHECKED_CAST")
        val envelope = mapAdapter.fromJson(lineSlot.captured) as Map<String, Any?>
        assertThat(envelope["type"]).isEqualTo(AnnouncementType.START_TIMER)
        assertThat(envelope["id"]).isEqualTo("id-timer")
        @Suppress("UNCHECKED_CAST")
        val payload = envelope["payload"] as Map<String, Any?>
        assertThat((payload["seconds"] as Number).toInt()).isEqualTo(300)
        assertThat(payload["label"]).isEqualTo("tea")

        // HMAC must be present and non-blank — the broadcaster's signing path
        // is already covered end-to-end by the TTS envelope test above; we
        // only need to verify the timer path actually signs what it sends.
        assertThat(envelope["hmac"] as? String).isNotEmpty()
    }

    @Test
    fun `broadcastTimer without label emits null label in payload`() = runTest {
        val peers = listOf(DiscoveredSpeaker("k", host = "10.0.0.2", port = 8421))
        val (d, self) = discovery(peers)
        val client = mockk<AnnouncementClient>()
        val lineSlot = slot<String>()
        coEvery { client.send(any(), any(), capture(lineSlot), any()) } returns SendOutcome.Ok

        val broadcaster = AnnouncementBroadcaster(
            discovery = d,
            client = client,
            securePreferences = securePrefs(secret),
            moshi = moshi,
            selfServiceName = self
        )

        val result = broadcaster.broadcastTimer(seconds = 60)
        assertThat(result.sentCount).isEqualTo(1)

        @Suppress("UNCHECKED_CAST")
        val envelope = mapAdapter.fromJson(lineSlot.captured) as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val payload = envelope["payload"] as Map<String, Any?>
        assertThat((payload["seconds"] as Number).toInt()).isEqualTo(60)
        // Null label: Moshi drops null entries on (de)serialization by default,
        // so we only require that no non-null label leaks into the wire payload.
        assertThat(payload["label"]).isNull()
    }

    @Test
    fun `broadcastTimer rejects non-positive seconds without sending`() = runTest {
        val peers = listOf(DiscoveredSpeaker("k", host = "10.0.0.2", port = 8421))
        val (d, self) = discovery(peers)
        val client = mockk<AnnouncementClient>()

        val broadcaster = AnnouncementBroadcaster(
            discovery = d,
            client = client,
            securePreferences = securePrefs(secret),
            moshi = moshi,
            selfServiceName = self
        )

        val result = broadcaster.broadcastTimer(seconds = 0)
        assertThat(result.sentCount).isEqualTo(0)
        assertThat(result.failures).hasSize(1)
        coVerify(exactly = 0) { client.send(any(), any(), any(), any()) }
    }

    @Test
    fun `broadcastTimer short-circuits when shared secret missing`() = runTest {
        val peers = listOf(DiscoveredSpeaker("k", host = "10.0.0.2", port = 8421))
        val (d, self) = discovery(peers)
        val client = mockk<AnnouncementClient>()

        val broadcaster = AnnouncementBroadcaster(
            discovery = d,
            client = client,
            securePreferences = securePrefs(null),
            moshi = moshi,
            selfServiceName = self
        )

        val result = broadcaster.broadcastTimer(seconds = 60, label = "tea")
        assertThat(result.sentCount).isEqualTo(0)
        assertThat(result.failures)
            .containsExactly("none" to SendOutcome.Other("no shared secret"))
        coVerify(exactly = 0) { client.send(any(), any(), any(), any()) }
    }

    @Test
    fun `broadcastCancelTimer with null id builds signed cancel_timer envelope with id=all`() = runTest {
        val peers = listOf(
            DiscoveredSpeaker("speaker-kitchen", host = "10.0.0.2", port = 8421),
            DiscoveredSpeaker("speaker-bedroom", host = "10.0.0.3", port = 8421)
        )
        val (d, self) = discovery(peers)
        val client = mockk<AnnouncementClient>()
        val lineSlot = slot<String>()
        coEvery { client.send(any(), any(), capture(lineSlot), any()) } returns SendOutcome.Ok

        val broadcaster = AnnouncementBroadcaster(
            discovery = d,
            client = client,
            securePreferences = securePrefs(secret),
            moshi = moshi,
            selfServiceName = self,
            clock = { 4_000L },
            idGenerator = { "id-cancel" }
        )

        val result = broadcaster.broadcastCancelTimer()

        assertThat(result.sentCount).isEqualTo(2)
        assertThat(result.failures).isEmpty()
        coVerify(exactly = 1) { client.send("10.0.0.2", 8421, any(), any()) }
        coVerify(exactly = 1) { client.send("10.0.0.3", 8421, any(), any()) }

        @Suppress("UNCHECKED_CAST")
        val envelope = mapAdapter.fromJson(lineSlot.captured) as Map<String, Any?>
        assertThat(envelope["type"]).isEqualTo(AnnouncementType.CANCEL_TIMER)
        assertThat(envelope["id"]).isEqualTo("id-cancel")
        @Suppress("UNCHECKED_CAST")
        val payload = envelope["payload"] as Map<String, Any?>
        assertThat(payload["id"]).isEqualTo("all")
        assertThat(envelope["hmac"] as? String).isNotEmpty()
    }

    @Test
    fun `broadcastCancelTimer with specific id forwards that id in payload`() = runTest {
        val peers = listOf(DiscoveredSpeaker("k", host = "10.0.0.2", port = 8421))
        val (d, self) = discovery(peers)
        val client = mockk<AnnouncementClient>()
        val lineSlot = slot<String>()
        coEvery { client.send(any(), any(), capture(lineSlot), any()) } returns SendOutcome.Ok

        val broadcaster = AnnouncementBroadcaster(
            discovery = d,
            client = client,
            securePreferences = securePrefs(secret),
            moshi = moshi,
            selfServiceName = self
        )

        val result = broadcaster.broadcastCancelTimer(id = "tid-42")
        assertThat(result.sentCount).isEqualTo(1)

        @Suppress("UNCHECKED_CAST")
        val envelope = mapAdapter.fromJson(lineSlot.captured) as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val payload = envelope["payload"] as Map<String, Any?>
        assertThat(payload["id"]).isEqualTo("tid-42")
    }

    @Test
    fun `broadcastCancelTimer with no peers reports zero sent without failures`() = runTest {
        val (d, self) = discovery(emptyList())
        val client = mockk<AnnouncementClient>()
        val broadcaster = AnnouncementBroadcaster(
            discovery = d,
            client = client,
            securePreferences = securePrefs(secret),
            moshi = moshi,
            selfServiceName = self
        )

        val result = broadcaster.broadcastCancelTimer()
        assertThat(result.sentCount).isEqualTo(0)
        assertThat(result.failures).isEmpty()
        coVerify(exactly = 0) { client.send(any(), any(), any(), any()) }
    }

    @Test
    fun `broadcastCancelTimer short-circuits when shared secret missing`() = runTest {
        val peers = listOf(DiscoveredSpeaker("k", host = "10.0.0.2", port = 8421))
        val (d, self) = discovery(peers)
        val client = mockk<AnnouncementClient>()

        val broadcaster = AnnouncementBroadcaster(
            discovery = d,
            client = client,
            securePreferences = securePrefs(null),
            moshi = moshi,
            selfServiceName = self
        )

        val result = broadcaster.broadcastCancelTimer()
        assertThat(result.sentCount).isEqualTo(0)
        assertThat(result.failures)
            .containsExactly("none" to SendOutcome.Other("no shared secret"))
        coVerify(exactly = 0) { client.send(any(), any(), any(), any()) }
    }

    @Test
    fun `broadcastHeartbeat builds signed heartbeat envelope with empty payload`() = runTest {
        val peers = listOf(
            DiscoveredSpeaker("speaker-kitchen", host = "10.0.0.2", port = 8421),
            DiscoveredSpeaker("speaker-bedroom", host = "10.0.0.3", port = 8421)
        )
        val (d, self) = discovery(peers)
        val client = mockk<AnnouncementClient>()
        val lineSlot = slot<String>()
        coEvery { client.send(any(), any(), capture(lineSlot), any()) } returns SendOutcome.Ok

        val broadcaster = AnnouncementBroadcaster(
            discovery = d,
            client = client,
            securePreferences = securePrefs(secret),
            moshi = moshi,
            selfServiceName = self,
            clock = { 5_000L },
            idGenerator = { "id-hb" }
        )

        val result = broadcaster.broadcastHeartbeat()
        assertThat(result.sentCount).isEqualTo(2)
        assertThat(result.failures).isEmpty()

        @Suppress("UNCHECKED_CAST")
        val envelope = mapAdapter.fromJson(lineSlot.captured) as Map<String, Any?>
        assertThat(envelope["type"]).isEqualTo(AnnouncementType.HEARTBEAT)
        assertThat(envelope["id"]).isEqualTo("id-hb")
        @Suppress("UNCHECKED_CAST")
        val payload = envelope["payload"] as Map<String, Any?>
        assertThat(payload).isEmpty()
        assertThat(envelope["hmac"] as? String).isNotEmpty()
    }

    @Test
    fun `broadcastHeartbeat short-circuits when shared secret missing`() = runTest {
        val peers = listOf(DiscoveredSpeaker("k", host = "10.0.0.2", port = 8421))
        val (d, self) = discovery(peers)
        val client = mockk<AnnouncementClient>()

        val broadcaster = AnnouncementBroadcaster(
            discovery = d,
            client = client,
            securePreferences = securePrefs(null),
            moshi = moshi,
            selfServiceName = self
        )

        val result = broadcaster.broadcastHeartbeat()
        assertThat(result.sentCount).isEqualTo(0)
        assertThat(result.failures)
            .containsExactly("none" to SendOutcome.Other("no shared secret"))
        coVerify(exactly = 0) { client.send(any(), any(), any(), any()) }
    }

    // -- Multi-room traffic counter wiring ------------------------------------

    @Test
    fun `broadcastTts records one outbound per peer that returned Ok`() = runTest {
        val peers = listOf(
            DiscoveredSpeaker("speaker-kitchen", host = "10.0.0.2", port = 8421),
            DiscoveredSpeaker("speaker-bedroom", host = "10.0.0.3", port = 8421)
        )
        val (d, self) = discovery(peers)
        val client = mockk<AnnouncementClient>()
        coEvery { client.send(any(), any(), any(), any()) } returns SendOutcome.Ok
        val recorder = io.mockk.mockk<MultiroomTrafficRecorder>(relaxed = true)

        val broadcaster = AnnouncementBroadcaster(
            discovery = d,
            client = client,
            securePreferences = securePrefs(secret),
            moshi = moshi,
            selfServiceName = self,
            trafficRecorder = recorder,
            nowMs = { 7_777L }
        )

        val result = broadcaster.broadcastTts("hi")
        assertThat(result.sentCount).isEqualTo(2)
        io.mockk.verify(exactly = 2) {
            recorder.recordOutbound(type = AnnouncementType.TTS_BROADCAST, nowMs = 7_777L)
        }
    }

    @Test
    fun `broadcastTts does not record outbound for peers whose send failed`() = runTest {
        // One good peer + one refused + one timeout: the recorder must
        // see exactly one outbound tick. Inflating the counter on a
        // flapping network would make the System Info view lie.
        val peers = listOf(
            DiscoveredSpeaker("ok", host = "10.0.0.2", port = 8421),
            DiscoveredSpeaker("down", host = "10.0.0.3", port = 8421),
            DiscoveredSpeaker("slow", host = "10.0.0.4", port = 8421)
        )
        val (d, self) = discovery(peers)
        val client = mockk<AnnouncementClient>()
        coEvery { client.send("10.0.0.2", 8421, any(), any()) } returns SendOutcome.Ok
        coEvery { client.send("10.0.0.3", 8421, any(), any()) } returns SendOutcome.ConnectionRefused
        coEvery { client.send("10.0.0.4", 8421, any(), any()) } returns SendOutcome.Timeout
        val recorder = io.mockk.mockk<MultiroomTrafficRecorder>(relaxed = true)

        val broadcaster = AnnouncementBroadcaster(
            discovery = d,
            client = client,
            securePreferences = securePrefs(secret),
            moshi = moshi,
            selfServiceName = self,
            trafficRecorder = recorder
        )

        broadcaster.broadcastTts("hi")
        io.mockk.verify(exactly = 1) { recorder.recordOutbound(type = AnnouncementType.TTS_BROADCAST, nowMs = any()) }
    }

    @Test
    fun `broadcastHeartbeat records outbound with heartbeat type`() = runTest {
        val peers = listOf(DiscoveredSpeaker("k", host = "10.0.0.2", port = 8421))
        val (d, self) = discovery(peers)
        val client = mockk<AnnouncementClient>()
        coEvery { client.send(any(), any(), any(), any()) } returns SendOutcome.Ok
        val recorder = io.mockk.mockk<MultiroomTrafficRecorder>(relaxed = true)

        val broadcaster = AnnouncementBroadcaster(
            discovery = d,
            client = client,
            securePreferences = securePrefs(secret),
            moshi = moshi,
            selfServiceName = self,
            trafficRecorder = recorder
        )

        broadcaster.broadcastHeartbeat()
        io.mockk.verify(exactly = 1) { recorder.recordOutbound(type = AnnouncementType.HEARTBEAT, nowMs = any()) }
    }

    @Test
    fun `broadcast without recorder wired still succeeds`() = runTest {
        // Existing tests and legacy call sites construct the broadcaster
        // without a recorder — ensure no NPE creeps into the happy path.
        val peers = listOf(DiscoveredSpeaker("k", host = "10.0.0.2", port = 8421))
        val (d, self) = discovery(peers)
        val client = mockk<AnnouncementClient>()
        coEvery { client.send(any(), any(), any(), any()) } returns SendOutcome.Ok

        val broadcaster = AnnouncementBroadcaster(
            discovery = d, client = client,
            securePreferences = securePrefs(secret), moshi = moshi, selfServiceName = self,
            trafficRecorder = null
        )

        val result = broadcaster.broadcastTts("hi")
        assertThat(result.sentCount).isEqualTo(1)
    }

    @Test
    fun `handoffConversation returns failure when no shared secret`() = runTest {
        val peers = listOf(DiscoveredSpeaker("speaker-kitchen", host = "10.0.0.2", port = 8421))
        val (d, self) = discovery(peers)
        val client = mockk<AnnouncementClient>()

        val broadcaster = AnnouncementBroadcaster(
            discovery = d,
            client = client,
            securePreferences = securePrefs(null),
            moshi = moshi,
            selfServiceName = self
        )

        val outcome = broadcaster.handoffConversation(
            targetServiceName = "kitchen",
            messages = listOf(AssistantMessage.User(content = "hi"))
        )
        assertThat(outcome).isInstanceOf(SendOutcome.Other::class.java)
        assertThat((outcome as SendOutcome.Other).reason).contains("secret")
    }
}
