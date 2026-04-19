package com.opendash.app.permission

import android.Manifest
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class PermissionCatalogTest {

    @Test
    fun `catalog includes microphone as non-optional`() {
        val mic = PermissionCatalog.entries().firstOrNull {
            it.permission == Manifest.permission.RECORD_AUDIO
        }
        assertThat(mic).isNotNull()
        assertThat(mic!!.optional).isFalse()
    }

    @Test
    fun `catalog includes calendar contacts location sms camera`() {
        val perms = PermissionCatalog.entries().map { it.permission }.toSet()

        assertThat(perms).contains(Manifest.permission.READ_CALENDAR)
        assertThat(perms).contains(Manifest.permission.READ_CONTACTS)
        assertThat(perms).contains(Manifest.permission.ACCESS_COARSE_LOCATION)
        assertThat(perms).contains(Manifest.permission.SEND_SMS)
        assertThat(perms).contains(Manifest.permission.CAMERA)
    }

    @Test
    fun `every entry has non-empty rationale and unlocks`() {
        for (entry in PermissionCatalog.entries()) {
            assertThat(entry.title).isNotEmpty()
            assertThat(entry.rationale).isNotEmpty()
            assertThat(entry.unlocks).isNotEmpty()
        }
    }

    @Test
    fun `special grants include notification listener and accessibility`() {
        val ids = PermissionCatalog.specialGrants.map { it.id }
        assertThat(ids).containsAtLeast("notification_listener", "accessibility")
    }

    @Test
    fun `special grants have valid settings actions`() {
        for (grant in PermissionCatalog.specialGrants) {
            assertThat(grant.settingsAction).startsWith("android.settings.")
        }
    }
}
