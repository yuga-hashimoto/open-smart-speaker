package com.opensmarthome.speaker.multiroom

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class HmacSignerTest {

    @Test
    fun `sign returns deterministic output for the same inputs`() {
        val a = HmacSigner.sign("secret", "tts_broadcast", "id1", 1_700_000_000L, "{\"text\":\"hi\"}")
        val b = HmacSigner.sign("secret", "tts_broadcast", "id1", 1_700_000_000L, "{\"text\":\"hi\"}")
        assertThat(a).isEqualTo(b)
        assertThat(a).isNotEmpty()
    }

    @Test
    fun `sign differs across secrets`() {
        val a = HmacSigner.sign("s1", "type", "id", 1L, "{}")
        val b = HmacSigner.sign("s2", "type", "id", 1L, "{}")
        assertThat(a).isNotEqualTo(b)
    }

    @Test
    fun `sign differs across any canonical field`() {
        val base = HmacSigner.sign("s", "type", "id", 1L, "{}")
        assertThat(HmacSigner.sign("s", "type", "id", 1L, "{\"x\":1}")).isNotEqualTo(base)
        assertThat(HmacSigner.sign("s", "type2", "id", 1L, "{}")).isNotEqualTo(base)
        assertThat(HmacSigner.sign("s", "type", "id2", 1L, "{}")).isNotEqualTo(base)
        assertThat(HmacSigner.sign("s", "type", "id", 2L, "{}")).isNotEqualTo(base)
    }

    @Test
    fun `verify accepts matching hmac`() {
        val sig = HmacSigner.sign("secret", "type", "id", 1L, "{}")
        assertThat(HmacSigner.verify("secret", "type", "id", 1L, "{}", sig)).isTrue()
    }

    @Test
    fun `verify rejects on any mismatch`() {
        val sig = HmacSigner.sign("secret", "type", "id", 1L, "{}")
        assertThat(HmacSigner.verify("wrong", "type", "id", 1L, "{}", sig)).isFalse()
        assertThat(HmacSigner.verify("secret", "other", "id", 1L, "{}", sig)).isFalse()
        assertThat(HmacSigner.verify("secret", "type", "id", 1L, "{}", "bogus")).isFalse()
    }

    @Test
    fun `canonical format is stable`() {
        // Pins the documented format from the ADR so accidental template edits
        // don't silently break interop with other speakers.
        assertThat(HmacSigner.canonical("t", "id", 42L, "{\"k\":1}")).isEqualTo("t|id|42|{\"k\":1}")
    }
}
