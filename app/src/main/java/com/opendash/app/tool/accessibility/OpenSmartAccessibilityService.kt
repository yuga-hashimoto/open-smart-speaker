package com.opendash.app.tool.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference

/**
 * Accessibility service that lets the agent read/control the screen.
 * Enables OpenClaw-style UI automation on Android without root.
 *
 * The user must grant Accessibility Service permission.
 * This is intentionally a thin skeleton — actions like click/swipe/scroll
 * will be added as follow-up commits after UX for permission request.
 */
class OpenSmartAccessibilityService : AccessibilityService() {

    companion object {
        /** Captures the last known root node for the ScreenReader tool. */
        private val instance = AtomicReference<OpenSmartAccessibilityService?>(null)

        fun currentInstance(): OpenSmartAccessibilityService? = instance.get()

        fun isRunning(): Boolean = instance.get() != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance.set(this)
        Timber.d("OpenSmartAccessibilityService connected")
    }

    override fun onInterrupt() {
        Timber.d("Accessibility service interrupted")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't react to events directly; ScreenReader queries root nodes
        // on demand via currentInstance().rootInActiveWindow.
    }

    override fun onDestroy() {
        super.onDestroy()
        instance.compareAndSet(this, null)
    }

    fun rootNode(): AccessibilityNodeInfo? = rootInActiveWindow
}
