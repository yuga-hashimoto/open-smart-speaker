package com.opendash.app.tool.system

/**
 * Reports paired Bluetooth devices and adapter state.
 * Backed by android.bluetooth.BluetoothAdapter on Android.
 */
interface BluetoothInfoProvider {
    /** True when BLUETOOTH_CONNECT (or legacy BLUETOOTH on <S) is granted. */
    fun hasPermission(): Boolean

    /** True when the Bluetooth radio is on. */
    fun isEnabled(): Boolean

    /** Returns the list of currently paired (bonded) devices. Empty if no permission. */
    suspend fun listPairedDevices(): List<BluetoothDeviceInfo>
}

data class BluetoothDeviceInfo(
    val name: String,
    val address: String,
    val type: String,
    val majorClass: String,
    val isConnected: Boolean
)
