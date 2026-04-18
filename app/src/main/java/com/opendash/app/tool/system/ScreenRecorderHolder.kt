package com.opendash.app.tool.system

import java.util.concurrent.atomic.AtomicReference

class ScreenRecorderHolder {
    private val ref = AtomicReference<ScreenRecorder>(NoOpScreenRecorder())

    fun setRecorder(recorder: ScreenRecorder) {
        ref.set(recorder)
    }

    fun clear() {
        ref.set(NoOpScreenRecorder())
    }

    fun current(): ScreenRecorder = ref.get()
}

class NoOpScreenRecorder : ScreenRecorder {
    override suspend fun start(request: RecordRequest): StartResult = StartResult.NeedsUserConsent
    override suspend fun stop(): StopResult = StopResult.NotRecording
    override fun isRecording(): Boolean = false
    override fun isReady(): Boolean = false
}
