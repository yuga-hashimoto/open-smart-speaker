package com.opendash.app.ui.ambient

import com.google.common.truth.Truth.assertThat
import com.opendash.app.device.model.Device
import com.opendash.app.device.model.DeviceState
import com.opendash.app.device.model.DeviceType
import com.opendash.app.tool.system.NotificationInfo
import com.opendash.app.tool.system.NotificationProvider
import com.opendash.app.tool.system.TimerInfo
import com.opendash.app.tool.system.TimerManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class AmbientSnapshotBuilderTest {

    @Test
    fun `extracts weather from first device with temperature`() = runTest {
        val builder = AmbientSnapshotBuilder(clock = { 1000L })
        val devices = listOf(
            device("d1", "Living Room Sensor", DeviceState(
                deviceId = "d1", temperature = 22.5f, humidity = 55f,
                attributes = mapOf("state" to "clear")
            ))
        )
        val snap = builder.build(devices)
        assertThat(snap.temperatureC).isEqualTo(22.5)
        assertThat(snap.humidityPercent).isEqualTo(55)
        assertThat(snap.weatherCondition).isEqualTo("clear")
    }

    @Test
    fun `counts active timers`() = runTest {
        val timer: TimerManager = mockk()
        coEvery { timer.getActiveTimers() } returns listOf(
            TimerInfo("t1", "pasta", 120, 300),
            TimerInfo("t2", "", 60, 300)
        )
        val builder = AmbientSnapshotBuilder(timerManager = timer, clock = { 0L })
        val snap = builder.build(emptyList())
        assertThat(snap.activeTimerCount).isEqualTo(2)
    }

    @Test
    fun `counts active notifications only when permission granted`() = runTest {
        val noPerm: NotificationProvider = mockk()
        every { noPerm.isListenerEnabled() } returns false
        val builderNoPerm = AmbientSnapshotBuilder(notificationProvider = noPerm, clock = { 0L })
        assertThat(builderNoPerm.build(emptyList()).activeNotificationCount).isEqualTo(0)

        val hasPerm: NotificationProvider = mockk()
        every { hasPerm.isListenerEnabled() } returns true
        coEvery { hasPerm.listNotifications() } returns listOf(
            NotificationInfo("x", "X", "t", "text", 0L, "k1"),
            NotificationInfo("y", "Y", "t", "text", 0L, "k2")
        )
        val builderWithPerm = AmbientSnapshotBuilder(notificationProvider = hasPerm, clock = { 0L })
        assertThat(builderWithPerm.build(emptyList()).activeNotificationCount).isEqualTo(2)
    }

    @Test
    fun `surfaces on devices as recent activity`() = runTest {
        val builder = AmbientSnapshotBuilder(clock = { 0L })
        val devices = listOf(
            device("l1", "Bedroom light", DeviceState("l1", isOn = true, lastUpdated = 100L)),
            device("l2", "Kitchen light", DeviceState("l2", isOn = true, lastUpdated = 200L)),
            device("l3", "Garage light", DeviceState("l3", isOn = false, lastUpdated = 300L))
        )
        val snap = builder.build(devices)
        // Only on-devices surface, sorted by lastUpdated desc
        assertThat(snap.recentDeviceActivity.map { it.name })
            .containsExactly("Kitchen light", "Bedroom light").inOrder()
    }

    @Test
    fun `media device shows playing state`() = runTest {
        val builder = AmbientSnapshotBuilder(clock = { 0L })
        val devices = listOf(
            device("m1", "Speaker", DeviceState("m1", mediaTitle = "Ambient Song"))
        )
        val snap = builder.build(devices)
        assertThat(snap.recentDeviceActivity.first().state).contains("Ambient Song")
    }

    @Test
    fun `failing timer source does not crash`() = runTest {
        val timer: TimerManager = mockk()
        coEvery { timer.getActiveTimers() } throws RuntimeException("boom")
        val builder = AmbientSnapshotBuilder(timerManager = timer, clock = { 0L })
        val snap = builder.build(emptyList())
        assertThat(snap.activeTimerCount).isEqualTo(0)
    }

    private fun device(id: String, name: String, state: DeviceState) = Device(
        id = id, providerId = "p", name = name, room = null,
        type = DeviceType.LIGHT, capabilities = emptySet(), state = state
    )
}
