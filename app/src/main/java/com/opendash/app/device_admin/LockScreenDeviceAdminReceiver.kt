package com.opendash.app.device_admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber

/**
 * Minimal DeviceAdminReceiver used by the `lock_screen` tool (P15.10).
 * The only policy we ask for is `force-lock` — see res/xml/device_admin_policies.xml.
 *
 * No other DPM privileges are requested; the receiver is declared in the
 * manifest but opt-in only — the user has to explicitly grant in Settings →
 * Security → Device admin apps. The Settings toggle in the app is plumbed by
 * a follow-up PR.
 */
class LockScreenDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Timber.d("Lock-screen device admin enabled by user")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Timber.d("Lock-screen device admin disabled by user")
    }
}
