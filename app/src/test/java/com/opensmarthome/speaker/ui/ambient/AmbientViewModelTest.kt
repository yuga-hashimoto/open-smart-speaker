package com.opensmarthome.speaker.ui.ambient

import com.google.common.truth.Truth.assertThat
import com.opensmarthome.speaker.device.DeviceManager
import com.opensmarthome.speaker.device.model.Device
import com.opensmarthome.speaker.device.model.DeviceState
import com.opensmarthome.speaker.device.model.DeviceType
import io.mockk.every
import io.mockk.mockk
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
class AmbientViewModelTest {

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
    fun `snapshot reflects device map from manager`() = runTest {
        val deviceManager: DeviceManager = mockk()
        val devices = MutableStateFlow(
            mapOf(
                "w1" to Device(
                    id = "w1", providerId = "p", name = "Weather",
                    room = null, type = DeviceType.OTHER, capabilities = emptySet(),
                    state = DeviceState(
                        deviceId = "w1", temperature = 21.0f, humidity = 48f,
                        attributes = mapOf("state" to "cloudy")
                    )
                )
            )
        )
        every { deviceManager.devices } returns devices

        val builder = AmbientSnapshotBuilder(clock = { 1_700_000_000_000L })
        val vm = AmbientViewModel(deviceManager, builder)
        advanceUntilIdle()

        val snap = vm.snapshot.value
        assertThat(snap.temperatureC).isEqualTo(21.0)
        assertThat(snap.humidityPercent).isEqualTo(48)
        assertThat(snap.weatherCondition).isEqualTo("cloudy")
    }

    @Test
    fun `snapshot updates when device map changes`() = runTest {
        val deviceManager: DeviceManager = mockk()
        val devices = MutableStateFlow<Map<String, Device>>(emptyMap())
        every { deviceManager.devices } returns devices

        val builder = AmbientSnapshotBuilder(clock = { 0L })
        val vm = AmbientViewModel(deviceManager, builder)
        advanceUntilIdle()
        assertThat(vm.snapshot.value.recentDeviceActivity).isEmpty()

        devices.value = mapOf(
            "l1" to Device(
                id = "l1", providerId = "p", name = "Living Light",
                room = null, type = DeviceType.LIGHT, capabilities = emptySet(),
                state = DeviceState(deviceId = "l1", isOn = true)
            )
        )
        advanceUntilIdle()

        assertThat(vm.snapshot.value.recentDeviceActivity).hasSize(1)
        assertThat(vm.snapshot.value.recentDeviceActivity.first().name).isEqualTo("Living Light")
    }
}
