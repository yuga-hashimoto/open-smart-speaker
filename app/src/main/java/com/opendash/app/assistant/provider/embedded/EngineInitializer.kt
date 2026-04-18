package com.opendash.app.assistant.provider.embedded

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import timber.log.Timber

/**
 * Initializes an LLM inference engine with GPU → CPU fallback and timeout protection.
 *
 * Stolen from off-grid-mobile-ai/src/services/llmHelpers.ts
 * - GPU init races against 8s timeout (prevents ANRs)
 * - On timeout or error → retry with CPU + reduced context
 */
class EngineInitializer(
    private val gpuTimeoutMs: Long = 8_000L,
    private val cpuTimeoutMs: Long = 30_000L
) {

    sealed class Result<out T> {
        data class Success<T>(val engine: T, val backend: Backend) : Result<T>()
        data class Failure(val gpuError: String?, val cpuError: String) : Result<Nothing>()
    }

    enum class Backend { GPU, CPU }

    /**
     * Initialize with GPU first, fall back to CPU on failure or timeout.
     * The caller supplies the initialization lambdas for each backend.
     */
    suspend fun <T> initialize(
        initGpu: suspend () -> T,
        initCpu: suspend () -> T
    ): Result<T> {
        val gpuErr = try {
            val engine = withTimeout(gpuTimeoutMs) { initGpu() }
            Timber.d("LLM engine initialized with GPU backend")
            return Result.Success(engine, Backend.GPU)
        } catch (e: TimeoutCancellationException) {
            Timber.w("GPU init timed out after ${gpuTimeoutMs}ms, falling back to CPU")
            "timeout after ${gpuTimeoutMs}ms"
        } catch (e: Exception) {
            Timber.w(e, "GPU init failed: ${e.message}, falling back to CPU")
            e.message ?: e.javaClass.simpleName
        }

        val cpuErr = try {
            val engine = withTimeout(cpuTimeoutMs) { initCpu() }
            Timber.d("LLM engine initialized with CPU backend (after GPU failure)")
            return Result.Success(engine, Backend.CPU)
        } catch (e: TimeoutCancellationException) {
            "CPU timeout after ${cpuTimeoutMs}ms"
        } catch (e: Exception) {
            Timber.e(e, "CPU init also failed")
            e.message ?: e.javaClass.simpleName
        }

        return Result.Failure(gpuError = gpuErr, cpuError = cpuErr)
    }
}
