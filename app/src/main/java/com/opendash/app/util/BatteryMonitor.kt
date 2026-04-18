package com.opendash.app.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-sample battery snapshot. `level` is a 0..100 integer; `isCharging` is
 * true while USB or AC power is connected. `isLow` is true when level is at
 * or below [BatteryMonitor.LOW_BATTERY_THRESHOLD] *and* the device is not
 * charging — the same condition Android's own "battery saver" uses as its
 * default trigger.
 */
data class BatteryStatus(
    val level: Int,
    val isCharging: Boolean
) {
    val isLow: Boolean
        get() = !isCharging && level in 0..BatteryMonitor.LOW_BATTERY_THRESHOLD
}

/**
 * Tracks the current battery level and charging state. Consumers use [isLow]
 * to back off background work (e.g. pause wake-word detection) under
 * user-configurable "battery saver" policies.
 */
@Singleton
class BatteryMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val status: StateFlow<BatteryStatus> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent != null) trySend(intent.toBatteryStatus())
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        // registerReceiver with a null receiver returns the current sticky intent.
        val sticky = context.applicationContext.registerReceiver(null, filter)
        trySend(sticky?.toBatteryStatus() ?: BatteryStatus(level = 100, isCharging = true))

        context.applicationContext.registerReceiver(receiver, filter)
        awaitClose {
            runCatching { context.applicationContext.unregisterReceiver(receiver) }
        }
    }.stateIn(scope, SharingStarted.Eagerly, BatteryStatus(level = 100, isCharging = true))

    companion object {
        /** Matches Android's default battery-saver trigger (≤ 20%). */
        const val LOW_BATTERY_THRESHOLD = 20

        internal fun Intent.toBatteryStatus(): BatteryStatus {
            val level = getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val pct = if (level >= 0 && scale > 0) (level * 100) / scale else 100
            val chargingState = getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            val plugged = getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            val isCharging = chargingState == BatteryManager.BATTERY_STATUS_CHARGING ||
                chargingState == BatteryManager.BATTERY_STATUS_FULL ||
                plugged != 0
            return BatteryStatus(level = pct, isCharging = isCharging)
        }
    }
}
