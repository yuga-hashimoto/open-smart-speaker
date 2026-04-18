package com.opendash.app.tool.system

import android.app.admin.DevicePolicyManager
import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.opendash.app.tool.ToolCall
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class LockScreenToolExecutorTest {

    private fun executor(
        context: Context,
        dpm: DevicePolicyManager
    ): LockScreenToolExecutor {
        // Stub packageName because the lazy ComponentName(context, cls) needs it.
        every { context.packageName } returns "com.opendash.app"
        return LockScreenToolExecutor(context, dpmProvider = { dpm })
    }

    @Test
    fun `available tools exposes lock_screen schema`() = runTest {
        val ctx = mockk<Context>()
        val dpm = mockk<DevicePolicyManager>()
        val schemas = executor(ctx, dpm).availableTools()
        assertThat(schemas.map { it.name }).containsExactly("lock_screen")
    }

    @Test
    fun `without admin grant returns failure with settings hint`() = runTest {
        val ctx = mockk<Context>(relaxed = true)
        val dpm = mockk<DevicePolicyManager>()
        every { dpm.isAdminActive(any()) } returns false
        val r = executor(ctx, dpm).execute(ToolCall("1", "lock_screen", emptyMap()))
        assertThat(r.success).isFalse()
        assertThat(r.error).contains("Device Admin")
        assertThat(r.error).contains("Settings")
    }

    @Test
    fun `with admin grant calls lockNow and reports success`() = runTest {
        val ctx = mockk<Context>(relaxed = true)
        val dpm = mockk<DevicePolicyManager>()
        every { dpm.isAdminActive(any()) } returns true
        every { dpm.lockNow() } just runs
        val r = executor(ctx, dpm).execute(ToolCall("2", "lock_screen", emptyMap()))
        assertThat(r.success).isTrue()
        assertThat(r.data).contains("\"locked\":true")
        verify(exactly = 1) { dpm.lockNow() }
    }

    @Test
    fun `SecurityException from lockNow surfaces as failure`() = runTest {
        val ctx = mockk<Context>(relaxed = true)
        val dpm = mockk<DevicePolicyManager>()
        every { dpm.isAdminActive(any()) } returns true
        every { dpm.lockNow() } throws SecurityException("system refused")
        val r = executor(ctx, dpm).execute(ToolCall("3", "lock_screen", emptyMap()))
        assertThat(r.success).isFalse()
        assertThat(r.error).contains("system refused")
    }

    @Test
    fun `unknown tool name returns failure without touching dpm`() = runTest {
        val ctx = mockk<Context>(relaxed = true)
        val dpm = mockk<DevicePolicyManager>()
        val r = executor(ctx, dpm).execute(ToolCall("4", "some_other_tool", emptyMap()))
        assertThat(r.success).isFalse()
        assertThat(r.error).contains("Unknown tool")
        verify(exactly = 0) { dpm.isAdminActive(any()) }
        verify(exactly = 0) { dpm.lockNow() }
    }
}
