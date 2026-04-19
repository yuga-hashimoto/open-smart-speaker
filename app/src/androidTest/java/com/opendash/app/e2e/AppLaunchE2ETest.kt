package com.opendash.app.e2e

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold-launch sanity check via UiAutomator.
 *
 * Launches the production [com.opendash.app.MainActivity] from a fresh
 * process and asserts that the first user-visible surface comes up
 * within [LAUNCH_TIMEOUT_MS]. Which surface that is depends on whether
 * the on-device LLM has already been downloaded on the test device:
 *
 * - First-ever install → ModelSetupScreen ("Select AI Model" / "Loading
 *   models from HuggingFace..." / "Downloading" / "Failed").
 * - Already downloaded → OnboardingScreen or ModeScaffold (the home /
 *   ambient surface). We accept any of these; the contract this test
 *   guards is "the app cold-starts without crashing and renders text",
 *   not which screen it lands on.
 *
 * If you change the labels on either screen, update [EXPECTED_TEXTS] so
 * this guard does not silently start passing on a blank screen.
 */
@RunWith(AndroidJUnit4::class)
class AppLaunchE2ETest {

    private lateinit var device: UiDevice

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    @Test
    fun cold_launch_renders_a_known_surface() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val packageName = context.packageName

        // Bring the app to foreground from launcher state.
        device.pressHome()
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?.apply { addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK) }
        assertThat(launchIntent).isNotNull()
        context.startActivity(launchIntent)

        // Wait for the app's window to be in front.
        val appearedInForeground = device.wait(
            Until.hasObject(By.pkg(packageName).depth(0)),
            LAUNCH_TIMEOUT_MS
        )
        assertThat(appearedInForeground).isTrue()

        // Wait for any of the known landing-screen labels.
        val landed = EXPECTED_TEXTS.any { text ->
            device.wait(Until.hasObject(By.textContains(text)), SCREEN_TEXT_TIMEOUT_MS) != null
        }
        assertThat(landed).isTrue()
    }

    private companion object {
        const val LAUNCH_TIMEOUT_MS = 10_000L
        const val SCREEN_TEXT_TIMEOUT_MS = 8_000L

        // Any one of these counts as "app rendered something". Order doesn't
        // matter — the loop polls each in turn.
        val EXPECTED_TEXTS = listOf(
            // ModelSetupScreen
            "Select AI Model",
            "Loading models",
            "Downloading",
            "Ready",
            "Failed",
            // OnboardingScreen / ModeScaffold (English fallback).
            "Welcome",
            "Get started",
            "Home",
            "Ambient"
        )
    }
}
