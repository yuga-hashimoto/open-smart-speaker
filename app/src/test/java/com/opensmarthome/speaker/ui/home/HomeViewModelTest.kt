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
        val vm = HomeViewModel(deviceManager, ss)
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
        val vm = HomeViewModel(deviceManager, ss)
        vm.dispatchMediaAction("media_player.x", MediaAction.PAUSE)
        advanceUntilIdle()

        coVerify { deviceManager.executeCommand(match { it.action == "media_pause" }) }
    }
}
