package com.opendash.app.device

import com.google.common.truth.Truth.assertThat
import com.opendash.app.device.model.CommandResult
import com.opendash.app.device.model.Device
import com.opendash.app.device.model.DeviceCommand
import com.opendash.app.device.model.DeviceState
import com.opendash.app.device.model.DeviceType
import com.opendash.app.device.provider.DeviceProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class DeviceManagerTest {

    private class FakeProvider(
        override val id: String,
        val devices: List<Device> = emptyList(),
        val available: Boolean = true,
        val commandResult: CommandResult = CommandResult(true, null)
    ) : DeviceProvider {
        override val displayName: String = id
        var lastCommand: DeviceCommand? = null

        override suspend fun discover(): List<Device> = devices
        override suspend fun getDevices(): List<Device> = devices
        override suspend fun getDeviceState(deviceId: String): DeviceState =
            devices.first { it.id == deviceId }.state
        override suspend fun executeCommand(command: DeviceCommand): CommandResult {
            lastCommand = command
            return commandResult
        }
        override fun stateChanges(): Flow<DeviceState> = emptyFlow()
        override suspend fun isAvailable(): Boolean = available
    }

    private fun device(id: String, providerId: String = "ha", type: DeviceType = DeviceType.LIGHT, room: String? = null) =
        Device(
            id = id, providerId = providerId, name = id,
            room = room, type = type,
            capabilities = emptySet(),
            state = DeviceState(deviceId = id)
        )

    @Test
    fun `refreshAll aggregates across providers`() = runTest {
        val ha = FakeProvider(id = "ha", devices = listOf(device("ha.l1")))
        val sb = FakeProvider(id = "switchbot", devices = listOf(device("sb.1", providerId = "switchbot")))
        val mgr = DeviceManager(providers = setOf(ha, sb))

        mgr.refreshAll()

        assertThat(mgr.devices.value.keys).containsExactly("ha.l1", "sb.1")
    }

    @Test
    fun `refreshAll skips unavailable providers`() = runTest {
        val available = FakeProvider(id = "ha", devices = listOf(device("ha.l1")))
        val offline = FakeProvider(id = "sb", devices = listOf(device("sb.1", "sb")), available = false)
        val mgr = DeviceManager(providers = setOf(available, offline))

        mgr.refreshAll()

        assertThat(mgr.devices.value.keys).containsExactly("ha.l1")
    }

    @Test
    fun `refreshAll tolerates a throwing provider`() = runTest {
        val good = FakeProvider(id = "good", devices = listOf(device("good.1", "good")))
        val broken = object : DeviceProvider {
            override val id = "broken"
            override val displayName = "broken"
            override suspend fun discover() = emptyList<Device>()
            override suspend fun getDevices(): List<Device> = error("exploded")
            override suspend fun getDeviceState(deviceId: String) = DeviceState(deviceId)
            override suspend fun executeCommand(command: DeviceCommand) = CommandResult(false, "no")
            override fun stateChanges() = emptyFlow<DeviceState>()
            override suspend fun isAvailable() = true
        }
        val mgr = DeviceManager(providers = setOf(good, broken))

        mgr.refreshAll()

        // The broken provider should be silently skipped (logged only).
        assertThat(mgr.devices.value.keys).containsExactly("good.1")
    }

    @Test
    fun `executeCommand routes to the device's provider`() = runTest {
        val ha = FakeProvider(id = "ha", devices = listOf(device("ha.l1")))
        val sb = FakeProvider(id = "sb", devices = listOf(device("sb.1", "sb")))
        val mgr = DeviceManager(providers = setOf(ha, sb))
        mgr.refreshAll()

        val command = DeviceCommand(deviceId = "sb.1", action = "turn_on")
        val result = mgr.executeCommand(command)

        assertThat(result.success).isTrue()
        assertThat(sb.lastCommand).isEqualTo(command)
        assertThat(ha.lastCommand).isNull()
    }

    @Test
    fun `executeCommand reports device-not-found`() = runTest {
        val mgr = DeviceManager(providers = setOf(FakeProvider("ha")))
        val result = mgr.executeCommand(DeviceCommand("ghost", "off"))
        assertThat(result.success).isFalse()
        assertThat(result.message).contains("Device not found")
    }

    @Test
    fun `executeCommand catches provider exceptions`() = runTest {
        val boom = object : DeviceProvider {
            override val id = "boom"
            override val displayName = "boom"
            override suspend fun discover() = emptyList<Device>()
            override suspend fun getDevices() = listOf(device("b1", "boom"))
            override suspend fun getDeviceState(deviceId: String) = DeviceState(deviceId)
            override suspend fun executeCommand(command: DeviceCommand): CommandResult =
                throw RuntimeException("network down")
            override fun stateChanges() = emptyFlow<DeviceState>()
            override suspend fun isAvailable() = true
        }
        val mgr = DeviceManager(providers = setOf(boom))
        mgr.refreshAll()

        val result = mgr.executeCommand(DeviceCommand("b1", "off"))
        assertThat(result.success).isFalse()
        assertThat(result.message).contains("network down")
    }

    @Test
    fun `getDevicesByType filters correctly`() = runTest {
        val provider = FakeProvider(
            id = "ha",
            devices = listOf(
                device("light-1", type = DeviceType.LIGHT),
                device("light-2", type = DeviceType.LIGHT),
                device("ac-1", type = DeviceType.CLIMATE)
            )
        )
        val mgr = DeviceManager(providers = setOf(provider))
        mgr.refreshAll()

        assertThat(mgr.getDevicesByType(DeviceType.LIGHT).map { it.id })
            .containsExactly("light-1", "light-2")
        assertThat(mgr.getDevicesByType(DeviceType.CLIMATE).map { it.id })
            .containsExactly("ac-1")
    }

    @Test
    fun `getDevicesByRoom is case-insensitive`() = runTest {
        val provider = FakeProvider(
            id = "ha",
            devices = listOf(
                device("a", room = "Living Room"),
                device("b", room = "living room"),
                device("c", room = "Kitchen")
            )
        )
        val mgr = DeviceManager(providers = setOf(provider))
        mgr.refreshAll()

        val living = mgr.getDevicesByRoom("living room").map { it.id }
        assertThat(living).containsExactly("a", "b")
    }

    @Test
    fun `getRooms returns distinct rooms with normalized ids`() = runTest {
        val provider = FakeProvider(
            id = "ha",
            devices = listOf(
                device("a", room = "Living Room"),
                device("b", room = "Living Room"),
                device("c", room = "Kitchen"),
                device("d", room = null)
            )
        )
        val mgr = DeviceManager(providers = setOf(provider))
        mgr.refreshAll()

        val rooms = mgr.getRooms().associateBy { it.name }
        assertThat(rooms.keys).containsExactly("Living Room", "Kitchen")
        assertThat(rooms["Living Room"]!!.id).isEqualTo("living_room")
    }

    @Test
    fun `getDevice returns null for unknown id`() = runTest {
        val mgr = DeviceManager(providers = emptySet())
        assertThat(mgr.getDevice("anything")).isNull()
    }
}
