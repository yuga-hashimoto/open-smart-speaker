package com.opensmarthome.speaker.multiroom

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.io.File

class PairingFingerprintTest {

    private val syntheticWordlist: List<String> = (0 until 256).map { "w$it" }

    @Test
    fun `same secret produces same phrase (deterministic)`() {
        val a = PairingFingerprint.forSecretInternal("super-secret-value", syntheticWordlist)
        val b = PairingFingerprint.forSecretInternal("super-secret-value", syntheticWordlist)
        assertThat(a).isEqualTo(b)
        assertThat(a).hasSize(PairingFingerprint.PHRASE_LENGTH)
    }

    @Test
    fun `different secrets produce different phrases (three samples)`() {
        val base = PairingFingerprint.forSecretInternal("secret-one", syntheticWordlist)
        val other1 = PairingFingerprint.forSecretInternal("secret-two", syntheticWordlist)
        val other2 = PairingFingerprint.forSecretInternal("totally-different-secret", syntheticWordlist)
        val other3 = PairingFingerprint.forSecretInternal("secret-one ", syntheticWordlist) // trailing space
        assertThat(other1).isNotEqualTo(base)
        assertThat(other2).isNotEqualTo(base)
        assertThat(other3).isNotEqualTo(base)
    }

    @Test
    fun `empty secret returns placeholder phrase of four unset words`() {
        val phrase = PairingFingerprint.forSecretInternal("", syntheticWordlist)
        assertThat(phrase).isEqualTo(
            List(PairingFingerprint.PHRASE_LENGTH) { PairingFingerprint.PLACEHOLDER_WORD }
        )
        assertThat(PairingFingerprint.PLACEHOLDER_WORD).isEqualTo("unset")
    }

    @Test
    fun `blank (whitespace) secret is treated as empty and returns placeholder`() {
        val phrase = PairingFingerprint.forSecretInternal("   ", syntheticWordlist)
        assertThat(phrase).isEqualTo(
            List(PairingFingerprint.PHRASE_LENGTH) { PairingFingerprint.PLACEHOLDER_WORD }
        )
    }

    @Test
    fun `wordlist size mismatch is rejected`() {
        val tooShort = listOf("only", "three", "words")
        val error = runCatching {
            PairingFingerprint.forSecretInternal("some-secret", tooShort)
        }.exceptionOrNull()
        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `bundled wordlist asset has exactly 256 unique non-empty words`() {
        // Pins the asset that ships with the APK so accidental edits to
        // wordlist-256.txt don't silently change every user's fingerprint.
        val asset = File("src/main/assets/pairing/wordlist-256.txt")
        assertThat(asset.exists()).isTrue()
        val words = asset.readLines().map { it.trim() }.filter { it.isNotEmpty() }
        assertThat(words).hasSize(256)
        assertThat(words.toSet()).hasSize(256) // no duplicates
        words.forEach { word ->
            assertThat(word.all { it.isLetter() }).isTrue()
            assertThat(word).isEqualTo(word.lowercase())
        }
    }

    @Test
    fun `phrase with real wordlist is reproducible for known secret`() {
        // Load the real asset so this test also catches divergence between
        // the synthetic test list and the shipped wordlist indexing logic.
        val realWords = File("src/main/assets/pairing/wordlist-256.txt")
            .readLines().map { it.trim() }.filter { it.isNotEmpty() }
        val secret = "open-smart-speaker-demo-secret"

        val phrase1 = PairingFingerprint.forSecretInternal(secret, realWords)
        val phrase2 = PairingFingerprint.forSecretInternal(secret, realWords)

        assertThat(phrase1).isEqualTo(phrase2)
        assertThat(phrase1).hasSize(4)
        phrase1.forEach { assertThat(realWords).contains(it) }
    }
}
