package com.opensmarthome.speaker.multiroom

import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HMAC-SHA256 signing / verification over the message canonical form
 * `type|id|ts|payload_json` per the ADR. Shared secret comes from
 * SecurePreferences once QR-pair lands (P17.6); today the user sets it
 * manually.
 *
 * Uses java.util.Base64 wrapping via android.util.Base64 so the code
 * runs on both Android runtime and JVM unit tests (the `Default` flag
 * == no line wrapping, matches the wire format).
 */
object HmacSigner {

    private const val ALGO = "HmacSHA256"

    fun sign(secret: String, type: String, id: String, ts: Long, payloadJson: String): String {
        val mac = Mac.getInstance(ALGO)
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), ALGO))
        val canonical = canonical(type, id, ts, payloadJson)
        val digest = mac.doFinal(canonical.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().withoutPadding().encodeToString(digest)
    }

    /**
     * Returns true iff [expected] base64-HMAC matches the canonical-form
     * signature of the other fields under [secret]. Constant-time compare.
     */
    fun verify(
        secret: String,
        type: String,
        id: String,
        ts: Long,
        payloadJson: String,
        expected: String
    ): Boolean {
        val actual = sign(secret, type, id, ts, payloadJson)
        return constantTimeEquals(actual, expected)
    }

    internal fun canonical(type: String, id: String, ts: Long, payloadJson: String): String =
        "$type|$id|$ts|$payloadJson"

    /** Constant-time string compare to dodge timing side channels. */
    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) {
            diff = diff or (a[i].code xor b[i].code)
        }
        return diff == 0
    }
}
