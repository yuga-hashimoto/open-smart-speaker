package com.opensmarthome.speaker.ui.common

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Regression guard for the status-bar-overlap fix.
 *
 * Android 15+ forces edge-to-edge rendering, and `MainActivity` explicitly
 * calls `enableEdgeToEdge()` + `WindowCompat.setDecorFitsSystemWindows(false)`.
 * Without `systemBarsPadding()` on top-level composables, content slides under
 * the status bar (and nav bar) and becomes unreadable — the exact bug users
 * reported as "画面が上のヘッダーと被って見にくい".
 *
 * We verify by source inspection rather than Compose UI tests because the unit
 * test target doesn't include the Compose testing runtime. This is enough to
 * catch an accidental removal of the inset padding in future refactors.
 */
class SystemBarsPaddingSourceTest {

    private fun sourceOf(relative: String): String {
        val file = File("src/main/java/com/opensmarthome/speaker/$relative")
        check(file.exists()) { "Expected source at ${file.absolutePath}" }
        return file.readText()
    }

    private fun assertHasInsetPadding(relative: String) {
        val src = sourceOf(relative)
        val mentionsInsetApi = src.contains("systemBarsPadding") ||
            src.contains("windowInsetsPadding")
        assertThat(mentionsInsetApi).isTrue()
    }

    @Test
    fun `ModeScaffold applies system bars padding to root layout`() {
        assertHasInsetPadding("ui/common/ModeScaffold.kt")
    }

    @Test
    fun `OnboardingScreen applies system bars padding to root layout`() {
        assertHasInsetPadding("ui/onboarding/OnboardingScreen.kt")
    }

    @Test
    fun `ModelSetupScreen applies system bars padding to root layout`() {
        assertHasInsetPadding("ui/setup/ModelSetupScreen.kt")
    }

    @Test
    fun `SettingsScreen applies system bars padding to root layout`() {
        assertHasInsetPadding("ui/settings/SettingsScreen.kt")
    }

    @Test
    fun `VoiceOverlay applies system bars padding to the full-screen Box`() {
        assertHasInsetPadding("ui/voice/VoiceOverlay.kt")
    }

    @Test
    fun `HomeScreen applies system bars padding to the root layout`() {
        assertHasInsetPadding("ui/home/HomeScreen.kt")
    }

    @Test
    fun `AmbientScreen applies system bars padding to the root layout`() {
        assertHasInsetPadding("ui/ambient/AmbientScreen.kt")
    }
}
