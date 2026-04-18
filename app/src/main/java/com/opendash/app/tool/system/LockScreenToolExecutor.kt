package com.opendash.app.tool.system

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import com.opendash.app.device_admin.LockScreenDeviceAdminReceiver
import com.opendash.app.tool.ToolCall
import com.opendash.app.tool.ToolExecutor
import com.opendash.app.tool.ToolResult
import com.opendash.app.tool.ToolSchema
import timber.log.Timber

/**
 * `lock_screen` tool (P15.10) — opt-in power action. Calls
 * DevicePolicyManager.lockNow() when the user has explicitly granted the
 * LockScreenDeviceAdminReceiver in system Settings. Without the grant the
 * tool returns a friendly message directing the user to the grant flow.
 */
class LockScreenToolExecutor(
    private val context: Context,
    /** Test hook — defaults to the real DevicePolicyManager. */
    private val dpmProvider: () -> DevicePolicyManager = {
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    }
) : ToolExecutor {

    private val adminComponent: ComponentName by lazy {
        ComponentName(context, LockScreenDeviceAdminReceiver::class.java)
    }

    override suspend fun availableTools(): List<ToolSchema> = listOf(
        ToolSchema(
            name = "lock_screen",
            description = "Lock the device screen. Requires Device Admin grant in Settings → Security → Device admin apps.",
            parameters = emptyMap()
        )
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        if (call.name != "lock_screen") {
            return ToolResult(call.id, false, "", "Unknown tool: ${call.name}")
        }
        return try {
            val dpm = dpmProvider()
            if (!dpm.isAdminActive(adminComponent)) {
                ToolResult(
                    call.id, false, "",
                    "Device Admin isn't enabled. Grant it in Settings → Security → Device admin apps → OpenDash."
                )
            } else {
                dpm.lockNow()
                ToolResult(call.id, true, """{"locked":true,"spoken":"Locked."}""")
            }
        } catch (e: SecurityException) {
            Timber.w(e, "lockNow denied")
            ToolResult(call.id, false, "", e.message ?: "Lock refused by system")
        } catch (e: Exception) {
            Timber.w(e, "lockNow threw")
            ToolResult(call.id, false, "", e.message ?: "Lock failed")
        }
    }
}
