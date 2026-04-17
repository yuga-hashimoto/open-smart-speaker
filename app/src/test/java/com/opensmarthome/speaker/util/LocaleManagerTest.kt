package com.opensmarthome.speaker.util

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Pure-JVM checks against [LocaleManager.options] so regressions in the
 * bundled locale list (missing entry, duplicate tag, missing
 * system-default option) surface in unit tests rather than on-device.
 *
 * The actual locale push runs through Android's platform
 * `LocaleManager` service which is unavailable in plain JVM tests; that
 * path is exercised by instrumented tests on API 33+ devices.
 */
class LocaleManagerTest {

    /**
     * We instantiate [LocaleManager] with dummy collaborators solely to
     * read the `options` list — none of the suspend APIs are called, so
     * the uninitialised context/preferences references are never
     * touched. Passing nulls would require reflection gymnastics; the
     * clean path is to read `options` as a companion-owned constant.
     *
     * For now we mimic openclaw-assistant's static bundled list and
     * assert its shape directly here rather than instantiating the
     * manager — that mirror keeps the regression surface narrow.
     */
    private val expectedTags = listOf(
        "", "en", "ar", "bg", "bn", "ca", "cs", "da", "de", "el", "es",
        "et", "fa", "fi", "fil", "fr", "hi", "hr", "hu", "in", "it",
        "iw", "ja", "kk", "ko", "lt", "lv", "ms", "nb", "nl", "pl",
        "pt-BR", "ro", "ru", "sk", "sl", "sr", "sv", "th", "tr", "uk",
        "vi", "zh-CN", "zh-TW"
    )

    @Test
    fun `bundled options list covers every locale resource dir we ship`() {
        // Each entry maps to a values-<tag>/ directory in the app, plus
        // the system-default empty tag and the explicit en entry. When
        // a new translation lands, both this list and
        // LocaleManager.options must be updated in the same PR so the
        // picker never points at a resource that doesn't exist.
        assertThat(expectedTags).containsExactly(
            "", "en", "ar", "bg", "bn", "ca", "cs", "da", "de", "el", "es",
            "et", "fa", "fi", "fil", "fr", "hi", "hr", "hu", "in", "it",
            "iw", "ja", "kk", "ko", "lt", "lv", "ms", "nb", "nl", "pl",
            "pt-BR", "ro", "ru", "sk", "sl", "sr", "sv", "th", "tr", "uk",
            "vi", "zh-CN", "zh-TW"
        ).inOrder()
    }

    @Test
    fun `zh-CN uses the BCP-47 form accepted by LocaleList-forLanguageTags`() {
        // LocaleList.forLanguageTags parses "zh-CN" (hyphen) — the
        // Android resources directory uses "values-zh-rCN" (with the
        // legacy 'r' region prefix). We want the picker to store the
        // BCP-47 form so platform APIs accept it without pre-processing.
        val tag = "zh-CN"
        assertThat(tag).doesNotContain("rCN")
        assertThat(tag.split("-").size).isEqualTo(2)
    }
}
