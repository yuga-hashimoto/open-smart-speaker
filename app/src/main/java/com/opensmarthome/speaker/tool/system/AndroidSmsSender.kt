package com.opensmarthome.speaker.tool.system

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import timber.log.Timber

class AndroidSmsSender(
    private val context: Context
) : SmsSender {

    @SuppressLint("MissingPermission")
    override suspend fun send(phoneNumber: String, message: String): SmsResult {
        if (!hasPermission()) return SmsResult.NoPermission

        return try {
            val manager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            } ?: return SmsResult.Failed("SmsManager unavailable")

            // Split long messages into multi-part
            val parts = manager.divideMessage(message)
            if (parts.size == 1) {
                manager.sendTextMessage(phoneNumber, null, message, null, null)
            } else {
                manager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            }
            Timber.d("SMS sent to $phoneNumber (${parts.size} parts)")
            SmsResult.Sent
        } catch (e: Exception) {
            Timber.e(e, "SMS send failed")
            SmsResult.Failed(e.message ?: "Unknown error")
        }
    }

    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED
}
