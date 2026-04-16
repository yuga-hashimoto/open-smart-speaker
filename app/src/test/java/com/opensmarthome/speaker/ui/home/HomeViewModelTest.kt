package com.opensmarthome.speaker.ui.home

import com.google.common.truth.Truth.assertThat
import com.opensmarthome.speaker.device.DeviceManager
import com.opensmarthome.speaker.device.model.CommandResult
import com.opensmarthome.speaker.device.model.DeviceCommand
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `dispatchMediaAction sends HA media_play service`() = runTest {
        val deviceManager = mockk<DeviceManager>()
        every { deviceManager.devices } returns MutableStateFlow(emptyMap())
        val cmdSlot = slot<DeviceCommand>()
        coEvery { deviceManager.executeCommand(capture(cmdSlot)) } returns CommandResult(success = true)

        val ss = mockk<com.opensmarthome.speaker.assistant.proactive.SuggestionState>(relaxed = true)
        every { ss.current } returns MutableStateFlow(emptyList())
        val te = mockk<com.opensmarthome.speaker.tool.ToolExecutor>(relaxed = true)
        val vm = HomeViewModel(deviceManager, ss, te)
        vm.dispatchMediaAction("media_player.kitchen", MediaAction.PLAY)
        advanceUntilIdle()

        assertThat(cmdSlot.captured.deviceId).isEqualTo("media_player.kitchen")
        assertThat(cmdSlot.captured.action).isEqualTo("media_play")
    }

    @Test
    fun `media actions map to correct HA service names`() {
        assertThat(MediaAction.PLAY.haService).isEqualTo("media_play")
        assertThat(MediaAction.PAUSE.haService).isEqualTo("media_pause")
        assertThat(MediaAction.NEXT.haService).isEqualTo("media_next_track")
        assertThat(MediaAction.PREVIOUS.haService).isEqualTo("media_previous_track")
    }

    @Test
    fun `dispatchMediaAction for pause sends media_pause`() = runTest {
        val deviceManager = mockk<DeviceManager>()
        every { deviceManager.devices } returns MutableStateFlow(emptyMap())
        coEvery { deviceManager.executeCommand(any()) } returns CommandResult(success = true)

        val ss = mockk<com.opensmarthome.speaker.assistant.proactive.SuggestionState>(relaxed = true)
        every { ss.current } returns MutableStateFlow(emptyList())
        val te = mockk<com.opensmarthome.speaker.tool.ToolExecutor>(relaxed = true)
        val vm = HomeViewModel(deviceManager, ss, te)
        vm.dispatchMediaAction("media_player.x", MediaAction.PAUSE)
        advanceUntilIdle()

        coVerify { deviceManager.executeCommand(match { it.action == "media_pause" }) }
    }

    @Test
    fun `dispatchMediaVolume sends volume_set with clamped volume_level`() = runTest {
        val deviceManager = mockk<DeviceManager>()
        every { deviceManager.devices } returns MutableStateFlow(emptyMap())
        val cmdSlot = slot<DeviceCommand>()
        coEvery { deviceManager.executeCommand(capture(cmdSlot)) } returns CommandResult(success = true)

        val ss = mockk<com.opensmarthome.speaker.assistant.proactive.SuggestionState>(relaxed = true)
        every { ss.current } returns MutableStateFlow(emptyList())
        val te = mockk<com.opensmarthome.speaker.tool.ToolExecutor>(relaxed = true)
        val vm = HomeViewModel(deviceManager, ss, te)

        vm.dispatchMediaVolume("media_player.den", 0.42f)
        advanceUntilIdle()

        assertThat(cmdSlot.captured.deviceId).isEqualTo("media_player.den")
        assertThat(cmdSlot.captured.action).isEqualTo("volume_set")
        assertThat(cmdSlot.captured.parameters["volume_level"]).isEqualTo(0.42f)
    }

    @Test
    fun `dispatchShuffle sends shuffle_set with bool parameter`() = runTest {
        val deviceManager = mockk<DeviceManager>()
        every { deviceManager.devices } returns MutableStateFlow(emptyMap())
        val cmdSlot = slot<DeviceCommand>()
        coEvery { deviceManager.executeCommand(capture(cmdSlot)) } returns CommandResult(success = true)

        val ss = mockk<com.opensmarthome.speaker.assistant.proactive.SuggestionState>(relaxed = true)
        every { ss.current } returns MutableStateFlow(emptyList())
        val te = mockk<com.opensmarthome.speaker.tool.ToolExecutor>(relaxed = true)
        val vm = HomeViewModel(deviceManager, ss, te)

        vm.dispatchShuffle("media_player.lr", true)
        advanceUntilIdle()
        assertThat(cmdSlot.captured.action).isEqualTo("shuffle_set")
        assertThat(cmdSlot.captured.parameters["shuffle"]).isEqualTo(true)
    }

    @Test
    fun `dispatchRepeat sends repeat_set with HA string value`() = runTest {
        val deviceManager = mockk<DeviceManager>()
        every { deviceManager.devices } returns MutableStateFlow(emptyMap())
        val cmdSlot = slot<DeviceCommand>()
        coEvery { deviceManager.executeCommand(capture(cmdSlot)) } returns CommandResult(success = true)

        val ss = mockk<com.opensmarthome.speaker.assistant.proactive.SuggestionState>(relaxed = true)
        every { ss.current } returns MutableStateFlow(emptyList())
        val te = mockk<com.opensmarthome.speaker.tool.ToolExecutor>(relaxed = true)
        val vm = HomeViewModel(deviceManager, ss, te)

        vm.dispatchRepeat("media_player.br", RepeatMode.ALL)
        advanceUntilIdle()
        assertThat(cmdSlot.captured.action).isEqualTo("repeat_set")
        assertThat(cmdSlot.captured.parameters["repeat"]).isEqualTo("all")
    }

    @Test
    fun `RepeatMode next cycles off all one off`() {
        assertThat(RepeatMode.OFF.next()).isEqualTo(RepeatMode.ALL)
        assertThat(RepeatMode.ALL.next()).isEqualTo(RepeatMode.ONE)
        assertThat(RepeatMode.ONE.next()).isEqualTo(RepeatMode.OFF)
    }

    @Test
    fun `RepeatMode fromHa is case-insensitive and falls back to off`() {
        assertThat(RepeatMode.fromHa("one")).isEqualTo(RepeatMode.ONE)
        assertThat(RepeatMode.fromHa("ALL")).isEqualTo(RepeatMode.ALL)
        assertThat(RepeatMode.fromHa(null)).isEqualTo(RepeatMode.OFF)
        assertThat(RepeatMode.fromHa("bogus")).isEqualTo(RepeatMode.OFF)
    }

    @Test
    fun `dispatchMediaVolume clamps out-of-range values`() = runTest {
        val deviceManager = mockk<DeviceManager>()
        every { deviceManager.devices } returns MutableStateFlow(emptyMap())
        val cmdSlot = slot<DeviceCommand>()
        coEvery { deviceManager.executeCommand(capture(cmdSlot)) } returns CommandResult(success = true)

        val ss = mockk<com.opensmarthome.speaker.assistant.proactive.SuggestionState>(relaxed = true)
        every { ss.current } returns MutableStateFlow(emptyList())
        val te = mockk<com.opensmarthome.speaker.tool.ToolExecutor>(relaxed = true)
        val vm = HomeViewModel(deviceManager, ss, te)

        vm.dispatchMediaVolume("media_player.over", 1.7f)
        advanceUntilIdle()
        assertThat(cmdSlot.captured.parameters["volume_level"]).isEqualTo(1.0f)

        vm.dispatchMediaVolume("media_player.under", -0.2f)
        advanceUntilIdle()
        assertThat(cmdSlot.captured.parameters["volume_level"]).isEqualTo(0.0f)
    }
}
