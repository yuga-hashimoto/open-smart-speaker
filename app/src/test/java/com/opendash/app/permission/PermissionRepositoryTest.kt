package com.opendash.app.permission

import android.Manifest
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

class PermissionRepositoryTest {

    @Test
    fun `rows combine runtime and special grants`() {
        val manager: PermissionManager = mockk()
        every { manager.snapshot() } returns PermissionManager.State(
            entries = listOf(
                PermissionManager.EntryState(
                    entry = PermissionCatalog.Entry(
                        permission = Manifest.permission.RECORD_AUDIO,
                        title = "Mic",
                        rationale = "voice input",
                        unlocks = listOf("voice_input"),
                        optional = false
                    ),
                    granted = true
                )
            ),
            specialGrants = listOf(
                PermissionManager.SpecialGrantState(
                    grant = PermissionCatalog.SpecialGrant(
                        id = "notification_listener",
                        title = "Notification Access",
                        rationale = "read notifications",
                        unlocks = listOf("list_notifications"),
                        settingsAction = "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"
                    ),
                    granted = false
                )
            )
        )
        val repo = PermissionRepository(manager)

        val rows = repo.rows()
        assertThat(rows).hasSize(2)
        assertThat(rows[0].kind).isEqualTo(PermissionRepository.Row.Kind.RUNTIME)
        assertThat(rows[0].granted).isTrue()
        assertThat(rows[1].kind).isEqualTo(PermissionRepository.Row.Kind.SPECIAL)
        assertThat(rows[1].granted).isFalse()
    }

    @Test
    fun `ungrantedCount reflects all denied entries`() {
        val manager: PermissionManager = mockk()
        every { manager.snapshot() } returns PermissionManager.State(
            entries = (1..3).map { i ->
                PermissionManager.EntryState(
                    entry = PermissionCatalog.Entry(
                        permission = "p$i", title = "t$i", rationale = "r",
                        unlocks = listOf("x"), optional = true
                    ),
                    granted = i == 1
                )
            },
            specialGrants = emptyList()
        )
        val repo = PermissionRepository(manager)
        assertThat(repo.ungrantedCount()).isEqualTo(2)
    }
}
