package com.opendash.app.voice.fastpath

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class SettingsMatcherTest {

    @Test
    fun `english open wifi settings maps to wifi slug`() {
        val m = SettingsMatcher.tryMatch("open wifi settings")
        assertThat(m?.toolName).isEqualTo("open_settings_page")
        assertThat(m?.arguments).containsEntry("page", "wifi")
    }

    @Test
    fun `english wi-fi hyphen variant also works`() {
        val m = SettingsMatcher.tryMatch("open wi-fi settings")
        assertThat(m?.arguments).containsEntry("page", "wifi")
    }

    @Test
    fun `english bluetooth settings maps to bluetooth slug`() {
        val m = SettingsMatcher.tryMatch("bluetooth settings")
        assertThat(m?.arguments).containsEntry("page", "bluetooth")
    }

    @Test
    fun `english brightness settings maps to brightness slug`() {
        val m = SettingsMatcher.tryMatch("brightness settings")
        assertThat(m?.arguments).containsEntry("page", "brightness")
    }

    @Test
    fun `english sound settings maps to sound slug`() {
        val m = SettingsMatcher.tryMatch("sound settings")
        assertThat(m?.arguments).containsEntry("page", "sound")
    }

    @Test
    fun `english app list maps to apps slug`() {
        val m = SettingsMatcher.tryMatch("app list")
        assertThat(m?.arguments).containsEntry("page", "apps")
    }

    @Test
    fun `english accessibility settings maps to accessibility slug`() {
        val m = SettingsMatcher.tryMatch("accessibility settings")
        assertThat(m?.arguments).containsEntry("page", "accessibility")
    }

    @Test
    fun `bare settings maps to home slug`() {
        val m = SettingsMatcher.tryMatch("settings")
        assertThat(m?.arguments).containsEntry("page", "home")
    }

    @Test
    fun `japanese wifi settings maps to wifi slug`() {
        val m = SettingsMatcher.tryMatch("wi-fiの設定")
        assertThat(m?.arguments).containsEntry("page", "wifi")
    }

    @Test
    fun `japanese bluetooth settings maps to bluetooth slug`() {
        val m = SettingsMatcher.tryMatch("ブルートゥースの設定")
        assertThat(m?.arguments).containsEntry("page", "bluetooth")
    }

    @Test
    fun `japanese brightness maps to brightness slug`() {
        val m = SettingsMatcher.tryMatch("明るさ")
        assertThat(m?.arguments).containsEntry("page", "brightness")
    }

    @Test
    fun `japanese volume maps to volume slug`() {
        val m = SettingsMatcher.tryMatch("音量の設定")
        assertThat(m?.arguments).containsEntry("page", "volume")
    }

    @Test
    fun `japanese accessibility maps to accessibility slug`() {
        val m = SettingsMatcher.tryMatch("アクセシビリティ")
        assertThat(m?.arguments).containsEntry("page", "accessibility")
    }

    @Test
    fun `japanese bare settings maps to home slug`() {
        val m = SettingsMatcher.tryMatch("設定を開いて")
        assertThat(m?.arguments).containsEntry("page", "home")
    }

    @Test
    fun `unrelated timer utterance does not match`() {
        assertThat(SettingsMatcher.tryMatch("set a timer for 5 minutes")).isNull()
    }

    @Test
    fun `unrelated lights utterance does not match`() {
        assertThat(SettingsMatcher.tryMatch("turn off the lights")).isNull()
    }

    @Test
    fun `matcher slugs all resolve in the executor`() {
        // Every slug returned by the matcher must be recognized by the
        // executor so a fast-path hit never dead-ends on "Unknown settings page".
        val utterances = listOf(
            "open wifi settings",
            "bluetooth settings",
            "brightness settings",
            "display settings",
            "sound settings",
            "volume settings",
            "accessibility settings",
            "notification settings",
            "app list",
            "battery settings",
            "settings",
            "wi-fiの設定",
            "ブルートゥースの設定",
            "明るさ",
            "音量",
            "アクセシビリティ",
            "通知の設定",
            "アプリの設定",
            "バッテリーの設定",
            "設定"
        )
        for (u in utterances) {
            val slug = SettingsMatcher.tryMatch(u)?.arguments?.get("page") as? String
            assertThat(slug).isNotNull()
            val action = com.opendash.app.tool.system
                .OpenSettingsToolExecutor.slugToAction(slug!!)
            assertThat(action).isNotNull()
        }
    }

    @Test
    fun `router places settings matcher before launch app so open wifi settings is captured`() {
        val router = DefaultFastPathRouter()
        val match = router.match("open wifi settings")
        assertThat(match?.toolName).isEqualTo("open_settings_page")
        assertThat(match?.arguments).containsEntry("page", "wifi")
    }
}
