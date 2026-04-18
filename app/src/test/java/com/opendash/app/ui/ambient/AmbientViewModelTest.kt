package com.opendash.app.ui.ambient

import com.google.common.truth.Truth.assertThat
import com.opendash.app.device.DeviceManager
import com.opendash.app.device.model.Device
import com.opendash.app.device.model.DeviceState
import com.opendash.app.device.model.DeviceType
import com.opendash.app.multiroom.AnnouncementState
import com.opendash.app.tool.system.TimerInfo
import com.opendash.app.tool.system.TimerManager
import com.opendash.app.util.BatteryMonitor
import com.opendash.app.util.BatteryStatus
import com.opendash.app.util.DiscoveredSpeaker
import com.opendash.app.util.MulticastDiscovery
import com.opendash.app.util.ThermalLevel
import com.opendash.app.util.ThermalMonitor
import io.mockk.coVerify
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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

    /**
     * Builds a TimerManager mock that reports no active timers. Every test
     * gets its own so `coEvery` stubs don't leak across cases.
     */
    private fun emptyTimerManager(): TimerManager {
        val tm = mockk<TimerManager>()
        coEvery { tm.getActiveTimers() } returns emptyList()
        return tm
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
            val te = io.mockk.mockk<com.opendash.app.tool.ToolExecutor>(relaxed = true)
            val bm = mockk<BatteryMonitor>()
            every { bm.status } returns MutableStateFlow(BatteryStatus(level = 100, isCharging = true))
            val tm = mockk<ThermalMonitor>()
            every { tm.status } returns MutableStateFlow(ThermalLevel.NORMAL)
            val md = mockk<MulticastDiscovery>()
            every { md.speakers } returns MutableStateFlow(emptyList())
            AmbientViewModel(deviceManager, builder, te, bm, tm, md, AnnouncementState(TestScope()), emptyTimerManager())
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
            val te = io.mockk.mockk<com.opendash.app.tool.ToolExecutor>(relaxed = true)
            val bm = mockk<BatteryMonitor>()
            every { bm.status } returns MutableStateFlow(BatteryStatus(level = 100, isCharging = true))
            val tm = mockk<ThermalMonitor>()
            every { tm.status } returns MutableStateFlow(ThermalLevel.NORMAL)
            val md = mockk<MulticastDiscovery>()
            every { md.speakers } returns MutableStateFlow(emptyList())
            AmbientViewModel(deviceManager, builder, te, bm, tm, md, AnnouncementState(TestScope()), emptyTimerManager())
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
        val te = io.mockk.mockk<com.opendash.app.tool.ToolExecutor>(relaxed = true)
        val battery = MutableStateFlow(BatteryStatus(level = 42, isCharging = false))
        val bm = mockk<BatteryMonitor>()
        every { bm.status } returns battery
        val tm = mockk<ThermalMonitor>()
        every { tm.status } returns MutableStateFlow(ThermalLevel.NORMAL)
        val md = mockk<MulticastDiscovery>()
        every { md.speakers } returns MutableStateFlow(emptyList())

        val vm = AmbientViewModel(deviceManager, builder, te, bm, tm, md, AnnouncementState(TestScope()), emptyTimerManager())
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
        val te = io.mockk.mockk<com.opendash.app.tool.ToolExecutor>(relaxed = true)
        val bm = mockk<BatteryMonitor>()
        every { bm.status } returns MutableStateFlow(BatteryStatus(level = 100, isCharging = true))
        val thermal = MutableStateFlow(ThermalLevel.NORMAL)
        val tm = mockk<ThermalMonitor>()
        every { tm.status } returns thermal
        val md = mockk<MulticastDiscovery>()
        every { md.speakers } returns MutableStateFlow(emptyList())

        val vm = AmbientViewModel(deviceManager, builder, te, bm, tm, md, AnnouncementState(TestScope()), emptyTimerManager())
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
        val te = io.mockk.mockk<com.opendash.app.tool.ToolExecutor>(relaxed = true)
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

        val vm = AmbientViewModel(deviceManager, builder, te, bm, tm, md, announcement, emptyTimerManager())
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
        val te = io.mockk.mockk<com.opendash.app.tool.ToolExecutor>(relaxed = true)
        val bm = mockk<BatteryMonitor>()
        every { bm.status } returns MutableStateFlow(BatteryStatus(level = 100, isCharging = true))
        val tm = mockk<ThermalMonitor>()
        every { tm.status } returns MutableStateFlow(ThermalLevel.NORMAL)
        val peers = MutableStateFlow<List<DiscoveredSpeaker>>(emptyList())
        val md = mockk<MulticastDiscovery>()
        every { md.speakers } returns peers

        val vm = AmbientViewModel(deviceManager, builder, te, bm, tm, md, AnnouncementState(TestScope()), emptyTimerManager())
        advanceUntilIdle()
        assertThat(vm.snapshot.value.nearbySpeakerCount).isEqualTo(0)

        peers.value = listOf(
            DiscoveredSpeaker("peer-a"),
            DiscoveredSpeaker("peer-b", "10.0.0.2", 8421)
        )
        advanceUntilIdle()
        assertThat(vm.snapshot.value.nearbySpeakerCount).isEqualTo(2)
    }

    @Test
    fun `activeTimers initially empty and emits from TimerManager on tick`() = runTest {
        val deviceManager: DeviceManager = mockk()
        every { deviceManager.devices } returns MutableStateFlow(emptyMap())
        val builder = AmbientSnapshotBuilder(clock = { 0L })
        val te = io.mockk.mockk<com.opendash.app.tool.ToolExecutor>(relaxed = true)
        val bm = mockk<BatteryMonitor>()
        every { bm.status } returns MutableStateFlow(BatteryStatus(level = 100, isCharging = true))
        val thm = mockk<ThermalMonitor>()
        every { thm.status } returns MutableStateFlow(ThermalLevel.NORMAL)
        val md = mockk<MulticastDiscovery>()
        every { md.speakers } returns MutableStateFlow(emptyList())

        // Timer manager reports one active timer; poller should pick it up.
        val timerManager = mockk<TimerManager>()
        val sample = TimerInfo(
            id = "timer_abcd1234",
            label = "pasta",
            remainingSeconds = 300,
            totalSeconds = 300
        )
        coEvery { timerManager.getActiveTimers() } returns listOf(sample)

        val vm = AmbientViewModel(
            deviceManager, builder, te, bm, thm, md,
            AnnouncementState(TestScope()),
            timerManager
        )
        // Initial value before any subscriber collects is empty.
        assertThat(vm.activeTimers.value).isEmpty()

        // Subscribe so WhileSubscribed activates the upstream poller.
        val collector = vm.activeTimers.onEach { /* drain */ }.launchIn(backgroundScope)
        // Let the first emission land but do NOT advanceUntilIdle (would infinite-loop).
        advanceTimeBy(50L)

        assertThat(vm.activeTimers.value).hasSize(1)
        assertThat(vm.activeTimers.value.first().id).isEqualTo("timer_abcd1234")
        assertThat(vm.activeTimers.value.first().label).isEqualTo("pasta")
        assertThat(vm.activeTimers.value.first().remainingSeconds).isEqualTo(300)

        collector.cancel()
    }

    @Test
    fun `activeTimers updates on each tick as remaining shrinks`() = runTest {
        val deviceManager: DeviceManager = mockk()
        every { deviceManager.devices } returns MutableStateFlow(emptyMap())
        val builder = AmbientSnapshotBuilder(clock = { 0L })
        val te = io.mockk.mockk<com.opendash.app.tool.ToolExecutor>(relaxed = true)
        val bm = mockk<BatteryMonitor>()
        every { bm.status } returns MutableStateFlow(BatteryStatus(level = 100, isCharging = true))
        val thm = mockk<ThermalMonitor>()
        every { thm.status } returns MutableStateFlow(ThermalLevel.NORMAL)
        val md = mockk<MulticastDiscovery>()
        every { md.speakers } returns MutableStateFlow(emptyList())

        // Return a decreasing remaining on successive polls to simulate live tick.
        val timerManager = mockk<TimerManager>()
        coEvery { timerManager.getActiveTimers() } returnsMany listOf(
            listOf(TimerInfo("timer_a", "tea", 60, 60)),
            listOf(TimerInfo("timer_a", "tea", 59, 60)),
            listOf(TimerInfo("timer_a", "tea", 58, 60))
        )

        val vm = AmbientViewModel(
            deviceManager, builder, te, bm, thm, md,
            AnnouncementState(TestScope()),
            timerManager
        )
        val collector = vm.activeTimers.onEach { /* drain */ }.launchIn(backgroundScope)
        advanceTimeBy(50L)
        assertThat(vm.activeTimers.value.single().remainingSeconds).isEqualTo(60)

        // Advance one tick interval (1s). Poller should re-query TimerManager.
        advanceTimeBy(1_000L)
        assertThat(vm.activeTimers.value.single().remainingSeconds).isEqualTo(59)

        advanceTimeBy(1_000L)
        assertThat(vm.activeTimers.value.single().remainingSeconds).isEqualTo(58)

        collector.cancel()
    }

    @Test
    fun `onCancelTimer calls TimerManager cancelTimer with given id`() = runTest {
        val deviceManager: DeviceManager = mockk()
        every { deviceManager.devices } returns MutableStateFlow(emptyMap())
        val builder = AmbientSnapshotBuilder(clock = { 0L })
        val te = io.mockk.mockk<com.opendash.app.tool.ToolExecutor>(relaxed = true)
        val bm = mockk<BatteryMonitor>()
        every { bm.status } returns MutableStateFlow(BatteryStatus(level = 100, isCharging = true))
        val thm = mockk<ThermalMonitor>()
        every { thm.status } returns MutableStateFlow(ThermalLevel.NORMAL)
        val md = mockk<MulticastDiscovery>()
        every { md.speakers } returns MutableStateFlow(emptyList())

        val timerManager = mockk<TimerManager>()
        coEvery { timerManager.getActiveTimers() } returns emptyList()
        coEvery { timerManager.cancelTimer("timer_xyz") } returns true

        val vm = AmbientViewModel(
            deviceManager, builder, te, bm, thm, md,
            AnnouncementState(TestScope()),
            timerManager
        )

        vm.onCancelTimer("timer_xyz")
        advanceUntilIdle()

        coVerify { timerManager.cancelTimer("timer_xyz") }
    }
}
