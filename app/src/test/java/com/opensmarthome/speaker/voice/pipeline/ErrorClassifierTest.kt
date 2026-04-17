package com.opensmarthome.speaker.voice.pipeline

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ErrorClassifierTest {

    private val classifier = ErrorClassifier()

    @Test
    fun `no provider configured`() {
        val r = classifier.classify("No available provider configured")
        assertThat(r.category).isEqualTo(ErrorClassifier.Category.NO_PROVIDER)
        assertThat(r.canRetry).isFalse()
        assertThat(r.userSpokenMessage).contains("AI model")
    }

    @Test
    fun `stt failure from index out of range`() {
        val r = classifier.classify("list index out of range")
        assertThat(r.category).isEqualTo(ErrorClassifier.Category.STT_FAILURE)
        assertThat(r.canRetry).isTrue()
    }

    @Test
    fun `stt failure from no match`() {
        val r = classifier.classify("ERROR_NO_MATCH")
        assertThat(r.category).isEqualTo(ErrorClassifier.Category.STT_FAILURE)
    }

    @Test
    fun `llm timeout`() {
        val r = classifier.classify("Request timed out after 30s")
        assertThat(r.category).isEqualTo(ErrorClassifier.Category.LLM_TIMEOUT)
        assertThat(r.canRetry).isTrue()
    }

    @Test
    fun `network error from unable to resolve`() {
        val r = classifier.classify("Unable to resolve host api.example.com")
        assertThat(r.category).isEqualTo(ErrorClassifier.Category.NETWORK)
    }

    @Test
    fun `permission error`() {
        val r = classifier.classify("Calendar permission not granted")
        assertThat(r.category).isEqualTo(ErrorClassifier.Category.PERMISSION)
        assertThat(r.canRetry).isFalse()
    }

    @Test
    fun `tool execution failure`() {
        val r = classifier.classify("Tool execution failed: missing arguments")
        assertThat(r.category).isEqualTo(ErrorClassifier.Category.TOOL_FAILURE)
    }

    @Test
    fun `unknown error still gets friendly copy`() {
        val r = classifier.classify("something weird happened")
        assertThat(r.category).isEqualTo(ErrorClassifier.Category.UNKNOWN)
        assertThat(r.canRetry).isTrue()
        assertThat(r.userSpokenMessage).doesNotContain("Exception")
    }

    @Test
    fun `null input does not crash`() {
        val r = classifier.classify(null)
        assertThat(r.category).isEqualTo(ErrorClassifier.Category.UNKNOWN)
    }

    @Test
    fun `throwable cause is also inspected`() {
        val cause = RuntimeException("permission denied for camera")
        val r = classifier.classify(null, cause)
        assertThat(r.category).isEqualTo(ErrorClassifier.Category.PERMISSION)
    }

    @Test
    fun `local kind never blames network for network-shaped errors`() {
        // A local (embedded) LLM has no network dependency for inference.
        // If classifier sees 'connection' (e.g. from an unrelated tool) while
        // the active provider is LOCAL, we must not tell the user 'Network hiccup'.
        val r = classifier.classify(
            "Unable to resolve host api.example.com",
            kind = ErrorClassifier.ProviderKind.LOCAL
        )
        assertThat(r.category).isNotEqualTo(ErrorClassifier.Category.NETWORK)
        assertThat(r.userSpokenMessage.lowercase()).doesNotContain("network")
        assertThat(r.userSpokenMessage.lowercase()).doesNotContain("internet")
    }

    @Test
    fun `local kind surfaces local-engine failure category`() {
        val r = classifier.classify(
            "connection reset",
            kind = ErrorClassifier.ProviderKind.LOCAL
        )
        assertThat(r.category).isEqualTo(ErrorClassifier.Category.LOCAL_ENGINE)
        assertThat(r.canRetry).isTrue()
    }

    @Test
    fun `remote kind keeps network classification`() {
        val r = classifier.classify(
            "Unable to resolve host api.example.com",
            kind = ErrorClassifier.ProviderKind.REMOTE
        )
        assertThat(r.category).isEqualTo(ErrorClassifier.Category.NETWORK)
    }

    @Test
    fun `local model load failure is a local-engine failure`() {
        val r = classifier.classify(
            "Failed to load GGUF model file",
            kind = ErrorClassifier.ProviderKind.LOCAL
        )
        assertThat(r.category).isEqualTo(ErrorClassifier.Category.LOCAL_ENGINE)
    }

    @Test
    fun `multiroom no shared secret`() {
        val r = classifier.classify("Broadcast refused: no shared secret")
        assertThat(r.category).isEqualTo(ErrorClassifier.Category.MULTIROOM_NO_SECRET)
        assertThat(r.canRetry).isFalse()
        assertThat(r.userSpokenMessage).contains("shared secret")
    }

    @Test
    fun `multiroom hmac mismatch from parser reason`() {
        // AnnouncementParser.Reason enum name surfaces as HMAC_MISMATCH;
        // classifier normalises underscores so the matcher catches it.
        val r = classifier.classify("Envelope rejected: HMAC_MISMATCH")
        assertThat(r.category).isEqualTo(ErrorClassifier.Category.MULTIROOM_HMAC)
        assertThat(r.userSpokenMessage.lowercase()).contains("verify")
    }

    @Test
    fun `multiroom replay window reject`() {
        val r = classifier.classify("Envelope rejected: REPLAY_WINDOW age=120s")
        assertThat(r.category).isEqualTo(ErrorClassifier.Category.MULTIROOM_REPLAY)
        assertThat(r.userSpokenMessage.lowercase()).contains("network time")
    }

    @Test
    fun `multiroom no peers discovered`() {
        val r = classifier.classify("No peers found to broadcast to.")
        assertThat(r.category).isEqualTo(ErrorClassifier.Category.MULTIROOM_NO_PEERS)
        assertThat(r.canRetry).isTrue()
        assertThat(r.userSpokenMessage.lowercase()).contains("speakers")
    }
}
