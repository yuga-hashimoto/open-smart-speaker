package com.opensmarthome.speaker.multiroom

import android.content.Context
import java.security.MessageDigest

/**
 * Derives a short, human-readable "pairing phrase" from a multiroom shared
 * secret so users can visually verify that two speakers have the same secret
 * without sharing the secret itself.
 *
 * Design:
 *  - Hash the secret with SHA-256 and take the first 4 bytes (32 bits of
 *    fingerprint entropy). Enough to bind a pairing attempt against casual
 *    guess-probing on a LAN, cheap to read aloud.
 *  - Map each byte (0..255) to one word from a 256-word list in
 *    `assets/pairing/wordlist-256.txt` via `byte.toUByte().toInt()`.
 *  - The raw secret stays 256-bit and unexposed; the phrase is a fingerprint
 *    only. Different secrets produce different phrases with overwhelming
 *    probability.
 *
 * A blank / empty secret returns a deterministic placeholder phrase of four
 * `PLACEHOLDER_WORD` entries so the UI can render something stable instead of
 * a misleading fingerprint of the empty string.
 */
object PairingFingerprint {

    /** Word emitted for all four positions when the secret is blank. */
    const val PLACEHOLDER_WORD = "unset"

    /** Number of words in the pairing phrase. */
    const val PHRASE_LENGTH = 4

    private const val ASSET_PATH = "pairing/wordlist-256.txt"
    private const val EXPECTED_WORDLIST_SIZE = 256

    /**
     * Returns the 4-word pairing phrase for [secret].
     *
     * If [secret] is blank, returns `[PLACEHOLDER_WORD] * PHRASE_LENGTH`.
     * The wordlist is loaded once from assets and cached for subsequent
     * calls (see [wordlistCache]).
     */
    fun forSecret(secret: String, context: Context): List<String> {
        if (secret.isBlank()) return placeholderPhrase()
        val wordlist = loadWordlist(context)
        return forSecretInternal(secret, wordlist)
    }

    /**
     * Visible for testing: computes the phrase against an explicit [wordlist]
     * so unit tests don't need an Android Context.
     */
    internal fun forSecretInternal(secret: String, wordlist: List<String>): List<String> {
        require(wordlist.size == EXPECTED_WORDLIST_SIZE) {
            "Wordlist must contain exactly $EXPECTED_WORDLIST_SIZE entries, got ${wordlist.size}"
        }
        if (secret.isBlank()) return placeholderPhrase()
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(secret.toByteArray(Charsets.UTF_8))
        return (0 until PHRASE_LENGTH).map { i ->
            val idx = digest[i].toUByte().toInt() // already 0..255, guaranteed in bounds
            wordlist[idx]
        }
    }

    private fun placeholderPhrase(): List<String> = List(PHRASE_LENGTH) { PLACEHOLDER_WORD }

    private fun loadWordlist(context: Context): List<String> =
        wordlistCache ?: synchronized(this) {
            wordlistCache ?: readWordlistFromAssets(context).also { wordlistCache = it }
        }

    @Volatile
    private var wordlistCache: List<String>? = null

    private fun readWordlistFromAssets(context: Context): List<String> {
        val words = context.assets.open(ASSET_PATH).bufferedReader().use { reader ->
            reader.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toList()
        }
        check(words.size == EXPECTED_WORDLIST_SIZE) {
            "Expected $EXPECTED_WORDLIST_SIZE words in $ASSET_PATH but found ${words.size}"
        }
        return words
    }

    /** Test-only hook to reset the cache between runs. */
    internal fun resetCacheForTest() {
        synchronized(this) { wordlistCache = null }
    }
}
