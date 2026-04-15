package com.opensmarthome.speaker.tool.system

import com.opensmarthome.speaker.tool.ToolCall
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SystemToolExecutorTest {

    private lateinit var executor: SystemToolExecutor
    private lateinit var timerManager: TimerManager
    private lateinit var volumeManager: VolumeManager
    private lateinit var appLauncher: AppLauncher

    @BeforeEach
    fun setup() {
        timerManager = mockk(relaxed = true)
        volumeManager = mockk(relaxed = true)
        appLauncher = mockk(relaxed = true)
        executor = SystemToolExecutor(timerManager, volumeManager, appLauncher)
    }

    @Test
    fun `availableTools returns timer and volume tools`() = runTest {
        val tools = executor.availableTools()
        val names = tools.map { it.name }

        assertThat(names).contains("set_timer")
        assertThat(names).contains("cancel_timer")
        assertThat(names).contains("set_volume")
        assertThat(names).contains("get_volume")
    }

    @Test
    fun `set_timer creates timer with correct duration`() = runTest {
        coEvery { timerManager.setTimer(300, "Pizza timer") } returns "timer_1"

        val result = executor.execute(
            ToolCall(id = "1", name = "set_timer", arguments = mapOf(
                "seconds" to 300.0,
                "label" to "Pizza timer"
            ))
        )

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("timer_1")
        coVerify { timerManager.setTimer(300, "Pizza timer") }
    }

    @Test
    fun `cancel_timer cancels by id`() = runTest {
        coEvery { timerManager.cancelTimer("timer_1") } returns true

        val result = executor.execute(
            ToolCall(id = "2", name = "cancel_timer", arguments = mapOf(
                "timer_id" to "timer_1"
            ))
        )

        assertThat(result.success).isTrue()
    }

    @Test
    fun `set_volume sets level`() = runTest {
        coEvery { volumeManager.setVolume(50) } returns true

        val result = executor.execute(
            ToolCall(id = "3", name = "set_volume", arguments = mapOf(
                "level" to 50.0
            ))
        )

        assertThat(result.success).isTrue()
    }

    @Test
    fun `get_volume returns current level`() = runTest {
        coEvery { volumeManager.getVolume() } returns 75

        val result = executor.execute(
            ToolCall(id = "4", name = "get_volume", arguments = emptyMap())
        )

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("75")
    }

    @Test
    fun `get_datetime returns current date and time`() = runTest {
        val result = executor.execute(
            ToolCall(id = "5", name = "get_datetime", arguments = emptyMap())
        )

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("date")
        assertThat(result.data).contains("time")
        assertThat(result.data).contains("day")
        assertThat(result.data).contains("timezone")
    }

    @Test
    fun `launch_app launches app by name`() = runTest {
        coEvery { appLauncher.launchApp("YouTube") } returns true

        val result = executor.execute(
            ToolCall(id = "6", name = "launch_app", arguments = mapOf(
                "app_name" to "YouTube"
            ))
        )

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("YouTube")
        coVerify { appLauncher.launchApp("YouTube") }
    }

    @Test
    fun `launch_app returns error for unknown app`() = runTest {
        coEvery { appLauncher.launchApp("NonExistentApp") } returns false

        val result = executor.execute(
            ToolCall(id = "7", name = "launch_app", arguments = mapOf(
                "app_name" to "NonExistentApp"
            ))
        )

        assertThat(result.success).isFalse()
        assertThat(result.error).contains("not found")
    }

    @Test
    fun `list_apps returns installed apps`() = runTest {
        coEvery { appLauncher.listInstalledApps() } returns listOf(
            AppInfo("YouTube", "com.google.android.youtube"),
            AppInfo("Chrome", "com.android.chrome")
        )

        val result = executor.execute(
            ToolCall(id = "8", name = "list_apps", arguments = emptyMap())
        )

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("YouTube")
        assertThat(result.data).contains("Chrome")
    }

    @Test
    fun `availableTools includes app and datetime tools`() = runTest {
        val tools = executor.availableTools()
        val names = tools.map { it.name }

        assertThat(names).contains("get_datetime")
        assertThat(names).contains("launch_app")
        assertThat(names).contains("list_apps")
    }

    @Test
    fun `unknown tool returns error`() = runTest {
        val result = executor.execute(
            ToolCall(id = "99", name = "unknown_tool", arguments = emptyMap())
        )

        assertThat(result.success).isFalse()
    }
}
