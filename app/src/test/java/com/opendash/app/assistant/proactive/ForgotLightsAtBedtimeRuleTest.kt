package com.opendash.app.assistant.proactive

import com.google.common.truth.Truth.assertThat
import com.opendash.app.device.model.Device
import com.opendash.app.device.model.DeviceState
import com.opendash.app.device.model.DeviceType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ForgotLightsAtBedtimeRuleTest {

    private fun ctx(hour: Int) = ProactiveContext(
        nowMs = 1_700_000_000_000L,
        hourOfDay = hour,
        dayOfWeek = java.util.Calendar.WEDNESDAY,
    )

    private fun light(id: String, name: String, isOn: Boolean): Device = Device(
        id = id,
        providerId = "ha",
        name = name,
        type = DeviceType.LIGHT,
        capabilities = emptySet(),
        state = DeviceState(deviceId = id, isOn = isOn),
    )

    private fun other(id: String, type: DeviceType, isOn: Boolean): Device = Device(
        id = id,
        providerId = "ha",
        name = id,
        type = type,
        capabilities = emptySet(),
        state = DeviceState(deviceId = id, isOn = isOn),
    )

    private fun rule(devices: Collection<Device>) =
        ForgotLightsAtBedtimeRule(devicesSupplier = { devices })

    @Test
    fun `no suggestion during daytime`() = runTest {
        val devices = listOf(light("l1", "Living", true))
        val s = rule(devices).evaluate(ctx(hour = 14))
        assertThat(s).isNull()
    }

    @Test
    fun `no suggestion when no lights are on`() = runTest {
        val devices = listOf(
            light("l1", "Living", false),
            light("l2", "Kitchen", false),
        )
        val s = rule(devices).evaluate(ctx(hour = 23))
        assertThat(s).isNull()
    }

    @Test
    fun `single on light at 22h emits actionable suggestion`() = runTest {
        val devices = listOf(light("l1", "Living", true))
        val s = rule(devices).evaluate(ctx(hour = 22))
        assertThat(s).isNotNull()
        assertThat(s!!.priority).isEqualTo(Suggestion.Priority.NORMAL)
        assertThat(s.message).contains("Living")
        assertThat(s.suggestedAction?.toolName).isEqualTo("execute_command")
        assertThat(s.suggestedAction?.arguments?.get("action")).isEqualTo("turn_off")
    }

    @Test
    fun `two on lights join with and`() = runTest {
        val devices = listOf(
            light("l1", "Living", true),
            light("l2", "Kitchen", true),
        )
        val s = rule(devices).evaluate(ctx(hour = 23))
        assertThat(s!!.message).contains("Kitchen and Living")  // alphabetical
    }

    @Test
    fun `three or more lights collapses into and N others`() = runTest {
        val devices = listOf(
            light("l1", "Living", true),
            light("l2", "Kitchen", true),
            light("l3", "Bedroom", true),
            light("l4", "Office", true),
        )
        val s = rule(devices).evaluate(ctx(hour = 0))
        assertThat(s!!.message).contains("2 other lights")
    }

    @Test
    fun `ignores non-light devices that are on`() = runTest {
        val devices = listOf(
            other("tv1", DeviceType.MEDIA_PLAYER, true),
            other("th1", DeviceType.CLIMATE, true),
        )
        val s = rule(devices).evaluate(ctx(hour = 23))
        assertThat(s).isNull()
    }

    @Test
    fun `rule wraps midnight up to 2 am`() = runTest {
        val devices = listOf(light("l1", "Living", true))
        assertThat(rule(devices).evaluate(ctx(hour = 0))).isNotNull()
        assertThat(rule(devices).evaluate(ctx(hour = 2))).isNotNull()
        assertThat(rule(devices).evaluate(ctx(hour = 3))).isNull()
    }

    @Test
    fun `suggestion id encodes the set of on-lights for dedupe`() = runTest {
        val first = rule(
            listOf(light("l1", "Living", true), light("l2", "Kitchen", true))
        ).evaluate(ctx(hour = 22))
        val second = rule(
            listOf(light("l1", "Living", true))  // Kitchen turned off
        ).evaluate(ctx(hour = 22))
        assertThat(first!!.id).isNotEqualTo(second!!.id)
    }
}
