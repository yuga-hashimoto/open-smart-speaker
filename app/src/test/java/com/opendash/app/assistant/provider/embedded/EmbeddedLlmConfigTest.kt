package com.opendash.app.assistant.provider.embedded

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class EmbeddedLlmConfigTest {

    @Test
    fun `forHardware low tier uses small context`() {
        val profile = HardwareProfile.fromRamMb(3_500)
        val config = EmbeddedLlmConfig.forHardware(
            modelPath = "/tmp/m.litertlm",
            profile = profile
        )
        assertThat(config.contextSize).isEqualTo(512)
        assertThat(config.threads).isEqualTo(2)
    }

    @Test
    fun `forHardware flagship tier uses large context`() {
        val profile = HardwareProfile.fromRamMb(10_000)
        val config = EmbeddedLlmConfig.forHardware(
            modelPath = "/tmp/m.litertlm",
            profile = profile
        )
        assertThat(config.contextSize).isEqualTo(4096)
        assertThat(config.threads).isEqualTo(6)
    }

    @Test
    fun `forHardware passes through modelPath and prompt`() {
        val profile = HardwareProfile.fromRamMb(7_000)
        val custom = "You are a pirate."
        val config = EmbeddedLlmConfig.forHardware(
            modelPath = "/tmp/pirate.gguf",
            profile = profile,
            systemPrompt = custom
        )
        assertThat(config.modelPath).isEqualTo("/tmp/pirate.gguf")
        assertThat(config.systemPrompt).isEqualTo(custom)
    }

    @Test
    fun `default constructor still works`() {
        val config = EmbeddedLlmConfig(modelPath = "/x")
        assertThat(config.modelPath).isEqualTo("/x")
        assertThat(config.contextSize).isEqualTo(1024)
    }
}
