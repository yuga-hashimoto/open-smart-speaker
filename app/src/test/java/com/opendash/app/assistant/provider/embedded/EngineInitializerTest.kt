package com.opendash.app.assistant.provider.embedded

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class EngineInitializerTest {

    @Test
    fun `GPU success returns GPU backend result`() = runTest {
        val initializer = EngineInitializer(gpuTimeoutMs = 1000, cpuTimeoutMs = 1000)

        val result = initializer.initialize(
            initGpu = { "gpu_engine" },
            initCpu = { "cpu_engine" }
        )

        assertThat(result).isInstanceOf(EngineInitializer.Result.Success::class.java)
        val success = result as EngineInitializer.Result.Success
        assertThat(success.engine).isEqualTo("gpu_engine")
        assertThat(success.backend).isEqualTo(EngineInitializer.Backend.GPU)
    }

    @Test
    fun `GPU failure falls back to CPU`() = runTest {
        val initializer = EngineInitializer(gpuTimeoutMs = 1000, cpuTimeoutMs = 1000)

        val result = initializer.initialize(
            initGpu = { throw RuntimeException("GPU not supported") },
            initCpu = { "cpu_engine" }
        )

        assertThat(result).isInstanceOf(EngineInitializer.Result.Success::class.java)
        val success = result as EngineInitializer.Result.Success
        assertThat(success.backend).isEqualTo(EngineInitializer.Backend.CPU)
    }

    @Test
    fun `GPU timeout falls back to CPU`() = runTest {
        val initializer = EngineInitializer(gpuTimeoutMs = 100, cpuTimeoutMs = 1000)

        val result = initializer.initialize(
            initGpu = { delay(10_000); "gpu_engine" }, // too slow
            initCpu = { "cpu_engine" }
        )

        val success = result as EngineInitializer.Result.Success
        assertThat(success.backend).isEqualTo(EngineInitializer.Backend.CPU)
    }

    @Test
    fun `both GPU and CPU failure returns Failure with both errors`() = runTest {
        val initializer = EngineInitializer(gpuTimeoutMs = 100, cpuTimeoutMs = 100)

        val result = initializer.initialize<String>(
            initGpu = { throw RuntimeException("GPU error") },
            initCpu = { throw RuntimeException("CPU error") }
        )

        assertThat(result).isInstanceOf(EngineInitializer.Result.Failure::class.java)
        val failure = result as EngineInitializer.Result.Failure
        assertThat(failure.gpuError).contains("GPU")
        assertThat(failure.cpuError).contains("CPU")
    }
}
