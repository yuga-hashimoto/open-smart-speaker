package com.opensmarthome.speaker.ui.ambient

import com.google.common.truth.Truth.assertThat
import com.opensmarthome.speaker.device.DeviceManager
import com.opensmarthome.speaker.device.model.Device
import com.opensmarthome.speaker.device.model.DeviceState
import com.opensmarthome.speaker.device.model.DeviceType
import com.opensmarthome.speaker.multiroom.AnnouncementState
import com.opensmarthome.speaker.util.BatteryMonitor
import com.opensmarthome.speaker.util.BatteryStatus
import com.opensmarthome.speaker.util.DiscoveredSpeaker
import com.opensmarthome.speaker.util.MulticastDiscovery
import com.opensmarthome.speaker.util.ThermalLevel
import com.opensmarthome.speaker.util.ThermalMonitor
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
        val vm = run {
            val te = io.mockk.mockk<com.opensmarthome.speaker.tool.ToolExecutor>(relaxed = true)
            val bm = mockk<BatteryMonitor>()
            every { bm.status } returns MutableStateFlow(BatteryStatus(level = 100, isCharging = true))
            val tm = mockk<ThermalMonitor>()
            every { tm.status } returns MutableStateFlow(ThermalLevel.NORMAL)
            val md = mockk<MulticastDiscovery>()
            every { md.speakers } returns MutableStateFlow(emptyList())
            AmbientViewModel(deviceManager, builder, te, bm, tm, md, AnnouncementState(TestScope()))
        }
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
        val vm = run {
            val te = io.mockk.mockk<com.opensmarthome.speaker.tool.ToolExecutor>(relaxed = true)
            val bm = mockk<BatteryMonitor>()
            every { bm.status } returns MutableStateFlow(BatteryStatus(level = 100, isCharging = true))
            val tm = mockk<ThermalMonitor>()
            every { tm.status } returns MutableStateFlow(ThermalLevel.NORMAL)
            val md = mockk<MulticastDiscovery>()
            every { md.speakers } returns MutableStateFlow(emptyList())
            AmbientViewModel(deviceManager, builder, te, bm, tm, md, AnnouncementState(TestScope()))
        }
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

    @Test
    fun `snapshot reflects battery level and charging state`() = runTest {
        val deviceManager: DeviceManager = mockk()
        every { deviceManager.devices } returns MutableStateFlow(emptyMap())
        val builder = AmbientSnapshotBuilder(clock = { 0L })
        val te = io.mockk.mockk<com.opensmarthome.speaker.tool.ToolExecutor>(relaxed = true)
        val battery = MutableStateFlow(BatteryStatus(level = 42, isCharging = false))
        val bm = mockk<BatteryMonitor>()
        every { bm.status } returns battery
        val tm = mockk<ThermalMonitor>()
        every { tm.status } returns MutableStateFlow(ThermalLevel.NORMAL)
        val md = mockk<MulticastDiscovery>()
        every { md.speakers } returns MutableStateFlow(emptyList())

        val vm = AmbientViewModel(deviceManager, builder, te, bm, tm, md, AnnouncementState(TestScope()))
        advanceUntilIdle()

        assertThat(vm.snapshot.value.batteryLevel).isEqualTo(42)
        assertThat(vm.snapshot.value.batteryCharging).isFalse()
        assertThat(vm.snapshot.value.thermalBucket).isNull()

        battery.value = BatteryStatus(level = 78, isCharging = true)
        advanceUntilIdle()

        assertThat(vm.snapshot.value.batteryLevel).isEqualTo(78)
        assertThat(vm.snapshot.value.batteryCharging).isTrue()
    }

    @Test
    fun `thermalBucket is populated only when state is non-NORMAL`() = runTest {
        val deviceManager: DeviceManager = mockk()
        every { deviceManager.devices } returns MutableStateFlow(emptyMap())
        val builder = AmbientSnapshotBuilder(clock = { 0L })
        val te = io.mockk.mockk<com.opensmarthome.speaker.tool.ToolExecutor>(relaxed = true)
        val bm = mockk<BatteryMonitor>()
        every { bm.status } returns MutableStateFlow(BatteryStatus(level = 100, isCharging = true))
        val thermal = MutableStateFlow(ThermalLevel.NORMAL)
        val tm = mockk<ThermalMonitor>()
        every { tm.status } returns thermal
        val md = mockk<MulticastDiscovery>()
        every { md.speakers } returns MutableStateFlow(emptyList())

        val vm = AmbientViewModel(deviceManager, builder, te, bm, tm, md, AnnouncementState(TestScope()))
        advanceUntilIdle()
        assertThat(vm.snapshot.value.thermalBucket).isNull()

        thermal.value = ThermalLevel.WARM
        advanceUntilIdle()
        assertThat(vm.snapshot.value.thermalBucket).isEqualTo("WARM")

        thermal.value = ThermalLevel.HOT
        advanceUntilIdle()
        assertThat(vm.snapshot.value.thermalBucket).isEqualTo("HOT")

        thermal.value = ThermalLevel.NORMAL
        advanceUntilIdle()
        assertThat(vm.snapshot.value.thermalBucket).isNull()
    }

    @Test
    fun `announcement text and from are piped through the snapshot`() = runTest {
        val deviceManager: DeviceManager = mockk()
        every { deviceManager.devices } returns MutableStateFlow(emptyMap())
        val builder = AmbientSnapshotBuilder(clock = { 0L })
        val te = io.mockk.mockk<com.opensmarthome.speaker.tool.ToolExecutor>(relaxed = true)
        val bm = mockk<BatteryMonitor>()
        every { bm.status } returns MutableStateFlow(BatteryStatus(level = 100, isCharging = true))
        val tm = mockk<ThermalMonitor>()
        every { tm.status } returns MutableStateFlow(ThermalLevel.NORMAL)
        val md = mockk<MulticastDiscovery>()
        every { md.speakers } returns MutableStateFlow(emptyList())
        // Use a real (non-Test) CoroutineScope for AnnouncementState so the
        // clear-timer's delay() isn't advanced by this test's
        // advanceUntilIdle() — runTest fast-forwards virtual time through
        // *all* pending delays on scopes it owns, which would prematurely
        // clear a long-TTL banner.
        val announcement = AnnouncementState(kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Unconfined
        ))

        announcement.setAnnouncement("dinner ready", ttlSeconds = 600, from = "kitchen")

        val vm = AmbientViewModel(deviceManager, builder, te, bm, tm, md, announcement)
        advanceUntilIdle()

        assertThat(vm.snapshot.value.announcementText).isEqualTo("dinner ready")
        assertThat(vm.snapshot.value.announcementFrom).isEqualTo("kitchen")

        // Dismissal flips the 5th flow back to null; the VM's combine must
        // re-run and produce a snapshot without the banner fields set.
        vm.dismissAnnouncement()
        advanceUntilIdle()
        assertThat(vm.snapshot.value.announcementText).isNull()
        assertThat(vm.snapshot.value.announcementFrom).isNull()
    }

    @Test
    fun `nearbySpeakerCount reflects MulticastDiscovery speakers list size`() = runTest {
        val deviceManager: DeviceManager = mockk()
        every { deviceManager.devices } returns MutableStateFlow(emptyMap())
        val builder = AmbientSnapshotBuilder(clock = { 0L })
        val te = io.mockk.mockk<com.opensmarthome.speaker.tool.ToolExecutor>(relaxed = true)
        val bm = mockk<BatteryMonitor>()
        every { bm.status } returns MutableStateFlow(BatteryStatus(level = 100, isCharging = true))
        val tm = mockk<ThermalMonitor>()
        every { tm.status } returns MutableStateFlow(ThermalLevel.NORMAL)
        val peers = MutableStateFlow<List<DiscoveredSpeaker>>(emptyList())
        val md = mockk<MulticastDiscovery>()
        every { md.speakers } returns peers

        val vm = AmbientViewModel(deviceManager, builder, te, bm, tm, md, AnnouncementState(TestScope()))
        advanceUntilIdle()
        assertThat(vm.snapshot.value.nearbySpeakerCount).isEqualTo(0)

        peers.value = listOf(
            DiscoveredSpeaker("peer-a"),
            DiscoveredSpeaker("peer-b", "10.0.0.2", 8421)
        )
        advanceUntilIdle()
        assertThat(vm.snapshot.value.nearbySpeakerCount).isEqualTo(2)
    }
}
