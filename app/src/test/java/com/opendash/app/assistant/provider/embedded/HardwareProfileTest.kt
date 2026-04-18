package com.opendash.app.assistant.provider.embedded

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class HardwareProfileTest {

    @Test
    fun `memory tier 3GB picks 1-5B model cap`() {
        val profile = HardwareProfile.fromRamMb(3_500)

        assertThat(profile.tier).isEqualTo(HardwareProfile.MemoryTier.LOW_3_4)
        assertThat(profile.recommendedParamsB).isAtMost(2)
        assertThat(profile.suggestedGpuLayers).isAtMost(16)
    }

    @Test
    fun `memory tier 5GB picks 3B model cap`() {
        val profile = HardwareProfile.fromRamMb(5_000)

        assertThat(profile.tier).isEqualTo(HardwareProfile.MemoryTier.MID_4_6)
        assertThat(profile.recommendedParamsB).isAtMost(3)
    }

    @Test
    fun `memory tier 7GB picks 4B model cap`() {
        val profile = HardwareProfile.fromRamMb(7_000)

        assertThat(profile.tier).isEqualTo(HardwareProfile.MemoryTier.HIGH_6_8)
        assertThat(profile.recommendedParamsB).isAtMost(4)
    }

    @Test
    fun `memory tier 10GB picks 8B model cap`() {
        val profile = HardwareProfile.fromRamMb(10_000)

        assertThat(profile.tier).isEqualTo(HardwareProfile.MemoryTier.FLAGSHIP_8_12)
        assertThat(profile.recommendedParamsB).isAtMost(8)
    }

    @Test
    fun `memory tier 16GB+ picks 13B model cap`() {
        val profile = HardwareProfile.fromRamMb(16_000)

        assertThat(profile.tier).isEqualTo(HardwareProfile.MemoryTier.TOP_12_PLUS)
        assertThat(profile.recommendedParamsB).isAtLeast(13)
    }

    @Test
    fun `thread count is conservative default 4`() {
        val profile = HardwareProfile.fromRamMb(8_000)
        assertThat(profile.recommendedThreads).isAtMost(8)
        assertThat(profile.recommendedThreads).isAtLeast(2)
    }
}
