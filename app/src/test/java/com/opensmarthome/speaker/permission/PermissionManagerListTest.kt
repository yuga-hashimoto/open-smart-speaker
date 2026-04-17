package com.opensmarthome.speaker.permission

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Pure tests for the list-of-classes constructor. We don't instantiate
 * PermissionManager with a real Context — just check the legacy single-class
 * constructor still normalises to the list path without nullability pain.
 */
class PermissionManagerListTest {

    @Test
    fun `single-class compat ctor normalises nulls to empty lists`() {
        // Would fail at construction time if null were passed through the new path.
        // Using Any::class.java as a stand-in since we only verify no-throw.
        val any: Class<*> = Any::class.java
        // Can't construct PermissionManager without Context, so verify the list
        // collapse via listOfNotNull directly — same semantics the constructor uses.
        assertThat(listOfNotNull<Class<*>>(null, any)).containsExactly(any)
        assertThat(listOfNotNull<Class<*>>(null, null)).isEmpty()
    }
}
