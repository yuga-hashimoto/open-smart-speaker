package com.opensmarthome.speaker.multiroom

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AnnouncementStateTest {

    private fun stateOn(scope: TestScope) = AnnouncementState(scope)

    @Test
    fun `setAnnouncement publishes active announcement`() = runTest {
        val state = stateOn(this)
        state.setAnnouncement("dinner is ready", 60, from = "kitchen")

        val active = state.activeAnnouncement.value
        assertThat(active).isNotNull()
        assertThat(active!!.text).isEqualTo("dinner is ready")
        assertThat(active.ttlSeconds).isEqualTo(60)
        assertThat(active.from).isEqualTo("kitchen")
    }

    @Test
    fun `setAnnouncement auto-clears after ttl_seconds`() = runTest {
        val state = stateOn(this)
        state.setAnnouncement("move car", 30, from = "garage")
        assertThat(state.activeAnnouncement.value).isNotNull()

        // Just before the TTL — banner is still there.
        advanceTimeBy(29_000L)
        assertThat(state.activeAnnouncement.value).isNotNull()

        // Crossing the TTL threshold clears it.
        advanceTimeBy(2_000L)
        advanceUntilIdle()
        assertThat(state.activeAnnouncement.value).isNull()
    }

    @Test
    fun `clear dismisses the banner immediately`() = runTest {
        val state = stateOn(this)
        state.setAnnouncement("hi", 600, from = null)
        assertThat(state.activeAnnouncement.value).isNotNull()

        state.clear()
        assertThat(state.activeAnnouncement.value).isNull()
    }

    @Test
    fun `setAnnouncement replaces an existing banner with fresh timer`() = runTest {
        val state = stateOn(this)
        state.setAnnouncement("first", 60, from = "a")
        advanceTimeBy(30_000L) // halfway through first
        state.setAnnouncement("second", 60, from = "b")

        assertThat(state.activeAnnouncement.value?.text).isEqualTo("second")

        // Advance past the first banner's would-be clear point (another
        // 31s → virtual time 61s). The first clear job should have been
        // cancelled when we published "second", so the banner survives.
        advanceTimeBy(31_000L)
        assertThat(state.activeAnnouncement.value?.text).isEqualTo("second")

        // Drive through the second job's own TTL (another 30s → virtual
        // time 91s, past the second job's 90s scheduled fire) and confirm
        // it clears.
        advanceTimeBy(30_000L)
        advanceUntilIdle()
        assertThat(state.activeAnnouncement.value).isNull()
    }

    @Test
    fun `blank text is ignored`() = runTest {
        val state = stateOn(this)
        state.setAnnouncement("   ", 60, from = "a")
        assertThat(state.activeAnnouncement.value).isNull()
    }

    @Test
    fun `non-positive ttl is ignored`() = runTest {
        val state = stateOn(this)
        state.setAnnouncement("hi", 0, from = "a")
        assertThat(state.activeAnnouncement.value).isNull()

        state.setAnnouncement("hi", -5, from = "a")
        assertThat(state.activeAnnouncement.value).isNull()
    }

    @Test
    fun `blank from is normalised to null on the DTO`() = runTest {
        val state = stateOn(this)
        state.setAnnouncement("hi", 60, from = "   ")
        assertThat(state.activeAnnouncement.value?.from).isNull()
    }

    @Test
    fun `uses injected dispatcher for deterministic timing`() = runTest(StandardTestDispatcher()) {
        // Smoke test — the primary guarantee is that TestScope.advanceTimeBy
        // drives the clear delay (covered above). This asserts the setup path.
        val state = AnnouncementState(this)
        state.setAnnouncement("ping", 5, from = "x")
        assertThat(state.activeAnnouncement.value).isNotNull()
        advanceTimeBy(6_000L)
        advanceUntilIdle()
        assertThat(state.activeAnnouncement.value).isNull()
    }
}
