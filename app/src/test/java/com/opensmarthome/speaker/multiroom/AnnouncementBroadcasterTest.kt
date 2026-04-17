package com.opensmarthome.speaker.multiroom

import com.google.common.truth.Truth.assertThat
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
}
