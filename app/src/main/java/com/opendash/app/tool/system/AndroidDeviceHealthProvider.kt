package com.opendash.app.tool.system

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs

class AndroidDeviceHealthProvider(
    private val context: Context
) : DeviceHealthProvider {

    override suspend fun snapshot(): DeviceHealth {
        val batteryIntent = context.registerReceiver(
            null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level > 0 && scale > 0) (level * 100 / scale) else null
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val tempTenths = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val tempC = if (tempTenths > 0) tempTenths / 10f else null

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val totalRam = memInfo.totalMem / (1024 * 1024)
        val availRam = memInfo.availMem / (1024 * 1024)

        val stat = StatFs(Environment.getDataDirectory().path)
        val totalStorage = (stat.blockSizeLong * stat.blockCountLong) / (1024 * 1024)
        val availStorage = (stat.blockSizeLong * stat.availableBlocksLong) / (1024 * 1024)

        return DeviceHealth(
            batteryPercent = percent,
            isCharging = isCharging,
            batteryTemperatureC = tempC,
            totalRamMb = totalRam,
            availableRamMb = availRam,
            totalStorageMb = totalStorage,
            availableStorageMb = availStorage,
            model = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidVersion = Build.VERSION.RELEASE
        )
    }
}
