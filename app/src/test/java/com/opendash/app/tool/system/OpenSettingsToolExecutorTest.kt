package com.opendash.app.tool.system

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.google.common.truth.Truth.assertThat
import com.opendash.app.tool.ToolCall
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class OpenSettingsToolExecutorTest {

    private data class Harness(
        val executor: OpenSettingsToolExecutor,
        val context: Context,
        val intent: Intent,
        val requestedAction: () -> String?,
        val flagsSet: () -> List<Int>,
        val startedIntents: List<Intent>
    )

    private fun newHarness(): Harness {
        val ctx = mockk<Context>(relaxed = true)
        val intent = mockk<Intent>(relaxed = true)
        var requestedAction: String? = null
        val flagsSet = mutableListOf<Int>()
        val startedIntents = mutableListOf<Intent>()

        every { intent.addFlags(any()) } answers {
            flagsSet.add(firstArg())
            intent
        }
        every { intent.action } answers { requestedAction }
        every { ctx.startActivity(any()) } answers {
            startedIntents.add(firstArg())
            Unit
        }

        val exec = OpenSettingsToolExecutor(
            context = ctx,
            intentFactory = { action ->
                requestedAction = action
                intent
            }
        )
        return Harness(exec, ctx, intent, { requestedAction }, { flagsSet.toList() }, startedIntents)
    }

    @Test
    fun `availableTools exposes open_settings_page with page enum`() = runTest {
        val h = newHarness()
        val tools = h.executor.availableTools()
        assertThat(tools.map { it.name }).containsExactly("open_settings_page")
        val param = tools.first().parameters["page"]
        assertThat(param).isNotNull()
        assertThat(param!!.required).isTrue()
        assertThat(param.enum).contains("wifi")
        assertThat(param.enum).contains("bluetooth")
    }

    @Test
    fun `each supported slug dispatches matching Settings action with NEW_TASK flag`() = runTest {
        val expected = mapOf(
            "wifi" to Settings.ACTION_WIFI_SETTINGS,
            "bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS,
            "display" to Settings.ACTION_DISPLAY_SETTINGS,
            "brightness" to Settings.ACTION_DISPLAY_SETTINGS,
            "sound" to Settings.ACTION_SOUND_SETTINGS,
            "volume" to Settings.ACTION_SOUND_SETTINGS,
            "accessibility" to Settings.ACTION_ACCESSIBILITY_SETTINGS,
            "notifications" to Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS,
            "apps" to Settings.ACTION_APPLICATION_SETTINGS,
            "battery" to Settings.ACTION_BATTERY_SAVER_SETTINGS,
            "home" to Settings.ACTION_SETTINGS,
            "main" to Settings.ACTION_SETTINGS
        )

        for ((slug, action) in expected) {
            val h = newHarness()
            val result = h.executor.execute(
                ToolCall(id = "call-$slug", name = "open_settings_page", arguments = mapOf("page" to slug))
            )

            assertThat(result.success).isTrue()
            assertThat(result.data).contains("\"page\":\"$slug\"")
            assertThat(result.data).contains("\"action\":\"$action\"")
            assertThat(h.requestedAction()).isEqualTo(action)
            assertThat(h.flagsSet()).contains(Intent.FLAG_ACTIVITY_NEW_TASK)
            assertThat(h.startedIntents).hasSize(1)
            assertThat(h.startedIntents.first()).isSameInstanceAs(h.intent)
        }
    }

    @Test
    fun `unknown page returns failure without starting activity`() = runTest {
        val h = newHarness()
        val result = h.executor.execute(
            ToolCall(id = "x", name = "open_settings_page", arguments = mapOf("page" to "quantum"))
        )
        assertThat(result.success).isFalse()
        assertThat(result.error).contains("Unknown settings page")
        assertThat(h.startedIntents).isEmpty()
        verify(exactly = 0) { h.context.startActivity(any()) }
    }

    @Test
    fun `missing page argument returns failure`() = runTest {
        val h = newHarness()
        val result = h.executor.execute(
            ToolCall(id = "x", name = "open_settings_page", arguments = emptyMap())
        )
        assertThat(result.success).isFalse()
        assertThat(result.error).contains("Missing page")
        assertThat(h.startedIntents).isEmpty()
    }

    @Test
    fun `unknown tool name returns failure`() = runTest {
        val h = newHarness()
        val result = h.executor.execute(
            ToolCall(id = "x", name = "some_other_tool", arguments = mapOf("page" to "wifi"))
        )
        assertThat(result.success).isFalse()
    }
}
