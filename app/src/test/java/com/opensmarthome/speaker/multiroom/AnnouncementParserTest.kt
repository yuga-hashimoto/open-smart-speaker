package com.opensmarthome.speaker.multiroom

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.junit.jupiter.api.Test

class AnnouncementParserTest {

    private val moshi = Moshi.Builder().build()
    private val mapAdapter = moshi.adapter<Map<String, Any?>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    )

    /** Builds a signed envelope line with the given fields. */
    private fun signedEnvelope(
        secret: String = "shhh",
        type: String = "tts_broadcast",
        id: String = "id-1",
        from: String = "speaker-kitchen",
        ts: Long = 1_000L,
        payload: Map<String, Any?> = mapOf("text" to "hello", "language" to "en"),
        v: Int = 1,
        hmacOverride: String? = null
    ): String {
        val payloadJson = mapAdapter.toJson(payload)
        val hmac = hmacOverride ?: HmacSigner.sign(secret, type, id, ts, payloadJson)
        val envelope = mapOf(
            "v" to v,
            "type" to type,
            "id" to id,
            "from" to from,
            "ts" to ts,
            "payload" to payload,
            "hmac" to hmac
        )
        return mapAdapter.toJson(envelope)
    }

    private fun parser(secret: String = "shhh", now: Long = 1_000L) =
        AnnouncementParser(moshi, sharedSecretProvider = { secret }, clock = { now })

    @Test
    fun `happy path returns Ok with envelope`() {
        val line = signedEnvelope()
        val result = parser().parse(line)
        assertThat(result).isInstanceOf(AnnouncementParser.ParseResult.Ok::class.java)
        val env = (result as AnnouncementParser.ParseResult.Ok).envelope
        assertThat(env.type).isEqualTo("tts_broadcast")
        assertThat(env.payload["text"]).isEqualTo("hello")
    }

    @Test
    fun `malformed json is rejected`() {
        val r = parser().parse("not-json")
        assertThat(r).isInstanceOf(AnnouncementParser.ParseResult.Rejected::class.java)
        assertThat((r as AnnouncementParser.ParseResult.Rejected).reason)
            .isEqualTo(AnnouncementParser.Reason.MALFORMED_JSON)
    }

    @Test
    fun `missing required field reports field name`() {
        // drop `hmac`
        val line = """{"v":1,"type":"t","id":"i","from":"f","ts":1,"payload":{}}"""
        val r = parser().parse(line) as AnnouncementParser.ParseResult.Rejected
        assertThat(r.reason).isEqualTo(AnnouncementParser.Reason.MISSING_FIELD)
        assertThat(r.detail).isEqualTo("hmac")
    }

    @Test
    fun `version mismatch is rejected with detail`() {
        val line = signedEnvelope(v = 99)
        val r = parser().parse(line) as AnnouncementParser.ParseResult.Rejected
        assertThat(r.reason).isEqualTo(AnnouncementParser.Reason.VERSION_MISMATCH)
    }

    @Test
    fun `old ts outside replay window is rejected`() {
        val line = signedEnvelope(ts = 100L)  // 900s ago
        val r = parser(now = 1_000L).parse(line) as AnnouncementParser.ParseResult.Rejected
        assertThat(r.reason).isEqualTo(AnnouncementParser.Reason.REPLAY_WINDOW)
    }

    @Test
    fun `missing secret rejects even with valid signature`() {
        val line = signedEnvelope()
        val p = AnnouncementParser(moshi, sharedSecretProvider = { null }, clock = { 1_000L })
        val r = p.parse(line) as AnnouncementParser.ParseResult.Rejected
        assertThat(r.reason).isEqualTo(AnnouncementParser.Reason.NO_SECRET)
    }

    @Test
    fun `bad hmac is rejected`() {
        val line = signedEnvelope(hmacOverride = "AAAA")
        val r = parser().parse(line) as AnnouncementParser.ParseResult.Rejected
        assertThat(r.reason).isEqualTo(AnnouncementParser.Reason.HMAC_MISMATCH)
    }

    @Test
    fun `hmac with wrong secret is rejected`() {
        val line = signedEnvelope(secret = "leaked")
        val r = parser(secret = "real").parse(line) as AnnouncementParser.ParseResult.Rejected
        assertThat(r.reason).isEqualTo(AnnouncementParser.Reason.HMAC_MISMATCH)
    }
}
