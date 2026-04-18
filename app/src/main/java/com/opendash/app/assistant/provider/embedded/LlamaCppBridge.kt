package com.opendash.app.assistant.provider.embedded

import timber.log.Timber

/**
 * JNI bridge to llama.cpp native library.
 *
 * The native library must be built from llama.cpp source with Android NDK
 * and included as a shared library (.so) in the app's jniLibs.
 *
 * Build instructions:
 * 1. Clone https://github.com/ggml-org/llama.cpp
 * 2. Build with CMake + Android NDK for arm64-v8a
 * 3. Place libllama_jni.so in app/src/main/jniLibs/arm64-v8a/
 */
class LlamaCppBridge {

    private var modelHandle: Long = 0L
    private var isLoaded = false

    companion object {
        private var libraryLoaded = false

        fun tryLoadLibrary(): Boolean {
            if (libraryLoaded) return true
            return try {
                System.loadLibrary("llama_jni")
                libraryLoaded = true
                true
            } catch (e: UnsatisfiedLinkError) {
                Timber.w("llama_jni native library not available: ${e.message}")
                false
            }
        }
    }

    fun loadModel(path: String, contextSize: Int, threads: Int, gpuLayers: Int): Boolean {
        if (!tryLoadLibrary()) {
            Timber.e("Cannot load model: native library not available")
            return false
        }
        return try {
            modelHandle = nativeLoadModel(path, contextSize, threads, gpuLayers)
            isLoaded = modelHandle != 0L
            isLoaded
        } catch (e: Exception) {
            Timber.e(e, "Failed to load model: $path")
            false
        }
    }

    fun generate(prompt: String, maxTokens: Int, temperature: Float): String {
        if (!isLoaded) throw IllegalStateException("Model not loaded")
        return nativeGenerate(modelHandle, prompt, maxTokens, temperature)
    }

    fun generateStreaming(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        callback: (String) -> Boolean
    ): String {
        if (!isLoaded) throw IllegalStateException("Model not loaded")
        return nativeGenerateStreaming(modelHandle, prompt, maxTokens, temperature, callback)
    }

    fun unload() {
        if (isLoaded && modelHandle != 0L) {
            nativeUnloadModel(modelHandle)
            modelHandle = 0L
            isLoaded = false
        }
    }

    fun isModelLoaded(): Boolean = isLoaded

    // Native methods - implemented in C/C++ JNI layer
    private external fun nativeLoadModel(path: String, contextSize: Int, threads: Int, gpuLayers: Int): Long
    private external fun nativeUnloadModel(handle: Long)
    private external fun nativeGenerate(handle: Long, prompt: String, maxTokens: Int, temperature: Float): String
    private external fun nativeGenerateStreaming(handle: Long, prompt: String, maxTokens: Int, temperature: Float, callback: (String) -> Boolean): String
}
