package com.opendash.app.util

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.PowerManager.OnThermalStatusChangedListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coarse thermal level derived from PowerManager.THERMAL_STATUS_*.
 * The app only cares about "is the device getting hot enough that we
 * should back off background work", so we collapse the seven Android
 * states into three buckets.
 */
enum class ThermalLevel {
    /** Thermal status NONE / LIGHT — safe to run normal workload. */
    NORMAL,

    /** MODERATE / SEVERE — back off long-running tasks when possible. */
    WARM,

    /** CRITICAL / EMERGENCY / SHUTDOWN — pause non-essential work. */
    HOT;

    val shouldThrottle: Boolean
        get() = this != NORMAL

    companion object {
        // PowerManager.THERMAL_STATUS_* integer literals. We bind to the numeric
        // values rather than the symbolic constants because the Android unit-test
        // stdlib mock resolves those constants to 0, which would collapse every
        // `when` branch onto NORMAL in tests. The values below match the Android
        // source of truth (API 29+).
        private const val STATUS_NONE = 0
        private const val STATUS_LIGHT = 1
        private const val STATUS_MODERATE = 2
        private const val STATUS_SEVERE = 3
        private const val STATUS_CRITICAL = 4
        private const val STATUS_EMERGENCY = 5
        private const val STATUS_SHUTDOWN = 6

        fun fromPlatformStatus(status: Int): ThermalLevel = when (status) {
            STATUS_NONE, STATUS_LIGHT -> NORMAL
            STATUS_MODERATE, STATUS_SEVERE -> WARM
            STATUS_CRITICAL, STATUS_EMERGENCY, STATUS_SHUTDOWN -> HOT
            else -> NORMAL
        }
    }
}

/**
 * Tracks the device's current thermal state via PowerManager. Consumers
 * should back off long-running background work (wake-word listening,
 * embedded LLM inference) when [status] is not [ThermalLevel.NORMAL].
 *
 * Below API 29 we always report NORMAL — the listener API isn't
 * available and polling isn't worth the code.
 */
@Singleton
class ThermalMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val powerManager by lazy {
        context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val status: StateFlow<ThermalLevel> = callbackFlow<ThermalLevel> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            trySend(ThermalLevel.NORMAL)
            awaitClose { }
            return@callbackFlow
        }

        trySend(ThermalLevel.fromPlatformStatus(powerManager.currentThermalStatus))

        val listener = PowerManager.OnThermalStatusChangedListener { raw ->
            trySend(ThermalLevel.fromPlatformStatus(raw))
        }
        val executor = Executor { command -> command.run() }
        runCatching {
            powerManager.addThermalStatusListener(executor, listener)
        }.onFailure { Timber.w(it, "addThermalStatusListener failed") }

        awaitClose {
            runCatching { powerManager.removeThermalStatusListener(listener) }
        }
    }.stateIn(scope, SharingStarted.Eagerly, ThermalLevel.NORMAL)

    /**
     * Non-reactive snapshot for callers that don't want to collect a flow.
     * Matches the semantics of [status.value] on API 29+; always NORMAL below.
     */
    fun current(): ThermalLevel = status.value
}
