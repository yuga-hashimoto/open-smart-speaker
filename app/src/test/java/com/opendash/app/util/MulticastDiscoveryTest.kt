package com.opendash.app.util

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Pure-logic tests for MulticastDiscovery. We don't instantiate the class here —
 * the rest of it is NsdManager glue, which belongs in instrumented tests. This
 * file pins down the default-name derivation that callers rely on when they pass
 * `instanceName = null` to [MulticastDiscovery.register].
 */
class MulticastDiscoveryTest {

    @Test
    fun `default instance name includes the model when non-blank`() {
        val name = MulticastDiscovery.defaultInstanceName("Pixel Tablet")
        assertThat(name).isEqualTo("OpenDash-Pixel Tablet")
    }

    @Test
    fun `default instance name trims whitespace from the model`() {
        val name = MulticastDiscovery.defaultInstanceName("  SM-T870  ")
        assertThat(name).isEqualTo("OpenDash-SM-T870")
    }

    @Test
    fun `default instance name falls back to Android when model is null`() {
        val name = MulticastDiscovery.defaultInstanceName(null)
        assertThat(name).isEqualTo("OpenDash-Android")
    }

    @Test
    fun `default instance name falls back to Android when model is blank`() {
        val name = MulticastDiscovery.defaultInstanceName("   ")
        assertThat(name).isEqualTo("OpenDash-Android")
    }

    @Test
    fun `service type constant is the multi-room mDNS type`() {
        assertThat(MulticastDiscovery.SERVICE_TYPE).isEqualTo("_opendash._tcp.")
    }

    @Test
    fun `default port is 8421`() {
        assertThat(MulticastDiscovery.DEFAULT_PORT).isEqualTo(8421)
    }
}
