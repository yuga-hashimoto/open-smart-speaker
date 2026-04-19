package com.opendash.app.tool.system

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import timber.log.Timber

class AndroidBluetoothInfoProvider(
    private val context: Context
) : BluetoothInfoProvider {

    private val adapter: BluetoothAdapter? by lazy {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        manager?.adapter
    }

    override fun hasPermission(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_CONNECT
        } else {
            Manifest.permission.BLUETOOTH
        }
        return ContextCompat.checkSelfPermission(context, perm) ==
            PackageManager.PERMISSION_GRANTED
    }

    override fun isEnabled(): Boolean = adapter?.isEnabled == true

    @Suppress("MissingPermission")
    override suspend fun listPairedDevices(): List<BluetoothDeviceInfo> {
        if (!hasPermission()) return emptyList()
        val a = adapter ?: return emptyList()

        return try {
            a.bondedDevices?.map { device ->
                BluetoothDeviceInfo(
                    name = device.name.orEmpty(),
                    address = device.address.orEmpty(),
                    type = describeType(device.type),
                    majorClass = describeMajorClass(device.bluetoothClass?.majorDeviceClass ?: -1),
                    isConnected = false // Connection state needs profile listeners; omit for now.
                )
            }.orEmpty()
        } catch (e: SecurityException) {
            Timber.w(e, "Bluetooth listing blocked")
            emptyList()
        } catch (e: Exception) {
            Timber.e(e, "Failed to list bonded BT devices")
            emptyList()
        }
    }

    private fun describeType(type: Int): String = when (type) {
        BluetoothDevice.DEVICE_TYPE_CLASSIC -> "classic"
        BluetoothDevice.DEVICE_TYPE_LE -> "le"
        BluetoothDevice.DEVICE_TYPE_DUAL -> "dual"
        else -> "unknown"
    }

    private fun describeMajorClass(major: Int): String = when (major) {
        BluetoothClass.Device.Major.AUDIO_VIDEO -> "audio_video"
        BluetoothClass.Device.Major.PHONE -> "phone"
        BluetoothClass.Device.Major.COMPUTER -> "computer"
        BluetoothClass.Device.Major.WEARABLE -> "wearable"
        BluetoothClass.Device.Major.HEALTH -> "health"
        BluetoothClass.Device.Major.IMAGING -> "imaging"
        BluetoothClass.Device.Major.PERIPHERAL -> "peripheral"
        BluetoothClass.Device.Major.TOY -> "toy"
        BluetoothClass.Device.Major.NETWORKING -> "networking"
        BluetoothClass.Device.Major.MISC -> "misc"
        BluetoothClass.Device.Major.UNCATEGORIZED -> "uncategorized"
        else -> "unknown"
    }
}
