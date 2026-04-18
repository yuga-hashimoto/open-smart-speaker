package com.opendash.app.voice.fastpath

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LocaleSwitchMatcherTest {

    private fun match(utterance: String): FastPathMatch? =
        LocaleSwitchMatcher.tryMatch(utterance.trim().lowercase())

    @Test
    fun `english switch to japanese routes to set_app_locale`() {
        val m = match("switch to Japanese")
        // English pattern requires "language|locale|ui" — bare "switch to X"
        // should not match. Use the full form.
        assertThat(m).isNull()

        val full = match("switch the language to Japanese")
        assertThat(full?.toolName).isEqualTo("set_app_locale")
        assertThat(full?.arguments).containsEntry("tag", "ja")
        assertThat(full?.spokenConfirmation).contains("日本語")
    }

    @Test
    fun `change language to spanish captures es tag`() {
        val m = match("change the language to Spanish")
        assertThat(m?.toolName).isEqualTo("set_app_locale")
        assertThat(m?.arguments).containsEntry("tag", "es")
    }

    @Test
    fun `set ui to french captures fr tag`() {
        val m = match("set the ui to French")
        assertThat(m?.toolName).isEqualTo("set_app_locale")
        assertThat(m?.arguments).containsEntry("tag", "fr")
    }

    @Test
    fun `japanese form 日本語にして captures ja tag`() {
        val m = match("日本語にして")
        assertThat(m?.toolName).isEqualTo("set_app_locale")
        assertThat(m?.arguments).containsEntry("tag", "ja")
    }

    @Test
    fun `japanese form フランス語に変えて captures fr tag`() {
        val m = match("フランス語に変えてください")
        assertThat(m?.toolName).isEqualTo("set_app_locale")
        assertThat(m?.arguments).containsEntry("tag", "fr")
    }

    @Test
    fun `portuguese captures pt-BR tag with canonical case`() {
        val m = match("change the language to Portuguese")
        assertThat(m?.toolName).isEqualTo("set_app_locale")
        // Canonical case preserved — LocaleManager uses case-sensitive tag compare.
        assertThat(m?.arguments).containsEntry("tag", "pt-BR")
    }

    @Test
    fun `chinese captures zh-CN tag with canonical case`() {
        val m = match("change the language to Simplified Chinese")
        assertThat(m?.toolName).isEqualTo("set_app_locale")
        assertThat(m?.arguments).containsEntry("tag", "zh-CN")
    }

    @Test
    fun `system default emits empty tag`() {
        val m = match("change the language to system default")
        assertThat(m?.toolName).isEqualTo("set_app_locale")
        assertThat(m?.arguments).containsEntry("tag", "")
    }

    @Test
    fun `system default via japanese emits empty tag`() {
        val m = match("システムにして")
        assertThat(m?.toolName).isEqualTo("set_app_locale")
        assertThat(m?.arguments).containsEntry("tag", "")
    }

    @Test
    fun `unrelated change the lights does not match`() {
        // Must fall through to smart-home matchers / LLM.
        assertThat(match("change the lights")).isNull()
    }

    @Test
    fun `japanese 6時にして does not match — language lookup misses`() {
        // "6時" is not a known language name, so the matcher falls through
        // rather than silently misrouting an alarm request.
        assertThat(match("6時にして")).isNull()
    }

    @Test
    fun `unknown language name falls through to LLM`() {
        // Klingon isn't in the bundle — don't fake a match, let LLM handle.
        assertThat(match("change the language to Klingon")).isNull()
    }

    @Test
    fun `korean captures ko tag`() {
        val m = match("change the language to Korean")
        assertThat(m?.toolName).isEqualTo("set_app_locale")
        assertThat(m?.arguments).containsEntry("tag", "ko")
    }

    @Test
    fun `russian via japanese label ロシア語 captures ru`() {
        val m = match("ロシア語に切り替えて")
        assertThat(m?.toolName).isEqualTo("set_app_locale")
        assertThat(m?.arguments).containsEntry("tag", "ru")
    }

    @Test
    fun `router slots locale switch matcher into default chain`() {
        val router = DefaultFastPathRouter()
        val m = router.match("change the language to German")
        assertThat(m?.toolName).isEqualTo("set_app_locale")
        assertThat(m?.arguments).containsEntry("tag", "de")
    }
}
