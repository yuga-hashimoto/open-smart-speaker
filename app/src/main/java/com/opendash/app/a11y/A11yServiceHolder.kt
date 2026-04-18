package com.opendash.app.a11y

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds a reference to the live [OpenDashA11yService] so that tool
 * executors (added in P15.2+) can call into the service without a static
 * backdoor. The service registers itself on [onServiceConnected] and clears
 * on [onUnbind].
 *
 * Also exposes the current foreground package as a [StateFlow] for observers
 * (e.g. future foreground-package-aware proactive rules).
 */
@Singleton
class A11yServiceHolder @Inject constructor(
    @Suppress("unused") @ApplicationContext private val context: Context
) {

    @Volatile
    var serviceRef: OpenDashA11yService? = null
        private set

    private val _currentPackage = MutableStateFlow<String?>(null)
    val currentPackage: StateFlow<String?> = _currentPackage.asStateFlow()

    /** Called from [OpenDashA11yService.onServiceConnected]. */
    fun attach(service: OpenDashA11yService) {
        serviceRef = service
    }

    /**
     * Called from [OpenDashA11yService.onUnbind]. Clears [serviceRef]
     * only if [service] is the currently attached instance to avoid racing
     * with a reattach from a fresh service process.
     */
    fun detach(service: OpenDashA11yService) {
        if (serviceRef === service) {
            serviceRef = null
            _currentPackage.value = null
        }
    }

    /** Called from [OpenDashA11yService.onAccessibilityEvent]. */
    fun updateCurrentPackage(packageName: String?) {
        _currentPackage.value = packageName
    }

    /** True once the user has enabled the service and Android has bound it. */
    fun isConnected(): Boolean = serviceRef != null
}
