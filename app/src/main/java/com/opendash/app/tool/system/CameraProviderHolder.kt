package com.opendash.app.tool.system

import java.util.concurrent.atomic.AtomicReference

/**
 * Holds the currently-active CameraProvider. Activities register themselves
 * via [setProvider] on onResume and clear via [clear] on onPause.
 * CameraToolExecutor reads from [current].
 */
class CameraProviderHolder {

    private val ref = AtomicReference<CameraProvider>(NoOpCameraProvider())

    fun setProvider(provider: CameraProvider) {
        ref.set(provider)
    }

    fun clear() {
        ref.set(NoOpCameraProvider())
    }

    fun current(): CameraProvider = ref.get()
}
