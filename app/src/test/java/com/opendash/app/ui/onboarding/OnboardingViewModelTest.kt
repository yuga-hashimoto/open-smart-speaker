package com.opendash.app.ui.onboarding

import com.google.common.truth.Truth.assertThat
import com.opendash.app.data.preferences.AppPreferences
import com.opendash.app.data.preferences.PreferenceKeys
import com.opendash.app.permission.PermissionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads permission rows on init`() = runTest {
        val repo = mockk<PermissionRepository>()
        every { repo.rows() } returns listOf(
            row(id = "mic", title = "Microphone", granted = true),
            row(id = "cam", title = "Camera", granted = false)
        )
        val prefs = mockk<AppPreferences>(relaxed = true)
        every { prefs.observe(PreferenceKeys.SETUP_COMPLETED) } returns flowOf(false)

        val vm = OnboardingViewModel(repo, prefs)
        advanceUntilIdle()

        assertThat(vm.state.value.rows).hasSize(2)
    }

    @Test
    fun `markCompleted writes SETUP_COMPLETED true`() = runTest {
        val repo = mockk<PermissionRepository>()
        every { repo.rows() } returns emptyList()
        val prefs = mockk<AppPreferences>(relaxed = true)
        every { prefs.observe(PreferenceKeys.SETUP_COMPLETED) } returns flowOf(false)
        coEvery { prefs.set(PreferenceKeys.SETUP_COMPLETED, true) } returns Unit

        val vm = OnboardingViewModel(repo, prefs)
        vm.markCompleted()
        advanceUntilIdle()

        coVerify { prefs.set(PreferenceKeys.SETUP_COMPLETED, true) }
    }

    @Test
    fun `refresh picks up newly granted permissions`() = runTest {
        val repo = mockk<PermissionRepository>()
        every { repo.rows() } returnsMany listOf(
            listOf(row(id = "cam", title = "Camera", granted = false)),
            listOf(row(id = "cam", title = "Camera", granted = true))
        )
        val prefs = mockk<AppPreferences>(relaxed = true)
        every { prefs.observe(PreferenceKeys.SETUP_COMPLETED) } returns flowOf(false)

        val vm = OnboardingViewModel(repo, prefs)
        advanceUntilIdle()
        assertThat(vm.state.value.rows.first().granted).isFalse()

        vm.refresh()
        advanceUntilIdle()
        assertThat(vm.state.value.rows.first().granted).isTrue()
    }

    private fun row(id: String, title: String, granted: Boolean) = PermissionRepository.Row(
        id = id, title = title, rationale = "r",
        unlocks = listOf("x"), granted = granted,
        kind = PermissionRepository.Row.Kind.RUNTIME
    )
}
