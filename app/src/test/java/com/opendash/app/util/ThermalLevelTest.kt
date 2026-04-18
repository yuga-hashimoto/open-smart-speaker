package com.opendash.app.util

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Pure unit tests for the THERMAL_STATUS_* (API 29+) → ThermalLevel mapping.
 * Uses integer literals so these don't depend on the Android stdlib mock
 * resolving PowerManager constants (it stubs them to 0, which would be
 * useless here).
 */
class ThermalLevelTest {

    @Test
    fun `status 0 and 1 map to NORMAL`() {
        assertThat(ThermalLevel.fromPlatformStatus(0)).isEqualTo(ThermalLevel.NORMAL) // NONE
        assertThat(ThermalLevel.fromPlatformStatus(1)).isEqualTo(ThermalLevel.NORMAL) // LIGHT
    }

    @Test
    fun `status 2 and 3 map to WARM`() {
        assertThat(ThermalLevel.fromPlatformStatus(2)).isEqualTo(ThermalLevel.WARM) // MODERATE
        assertThat(ThermalLevel.fromPlatformStatus(3)).isEqualTo(ThermalLevel.WARM) // SEVERE
    }

    @Test
    fun `status 4 5 and 6 map to HOT`() {
        assertThat(ThermalLevel.fromPlatformStatus(4)).isEqualTo(ThermalLevel.HOT) // CRITICAL
        assertThat(ThermalLevel.fromPlatformStatus(5)).isEqualTo(ThermalLevel.HOT) // EMERGENCY
        assertThat(ThermalLevel.fromPlatformStatus(6)).isEqualTo(ThermalLevel.HOT) // SHUTDOWN
    }

    @Test
    fun `unknown value falls back to NORMAL`() {
        assertThat(ThermalLevel.fromPlatformStatus(Int.MIN_VALUE)).isEqualTo(ThermalLevel.NORMAL)
        assertThat(ThermalLevel.fromPlatformStatus(9999)).isEqualTo(ThermalLevel.NORMAL)
        assertThat(ThermalLevel.fromPlatformStatus(-1)).isEqualTo(ThermalLevel.NORMAL)
    }

    @Test
    fun `shouldThrottle is true for WARM and HOT`() {
        assertThat(ThermalLevel.NORMAL.shouldThrottle).isFalse()
        assertThat(ThermalLevel.WARM.shouldThrottle).isTrue()
        assertThat(ThermalLevel.HOT.shouldThrottle).isTrue()
    }
}
