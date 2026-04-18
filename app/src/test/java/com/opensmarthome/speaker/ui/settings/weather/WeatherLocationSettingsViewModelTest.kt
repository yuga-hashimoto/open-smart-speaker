package com.opensmarthome.speaker.ui.settings.weather

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.opensmarthome.speaker.data.location.CitySearchRepository
import com.opensmarthome.speaker.data.location.CitySuggestion
import com.opensmarthome.speaker.data.preferences.AppPreferences
import com.opensmarthome.speaker.data.preferences.PreferenceKeys
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests for [WeatherLocationSettingsViewModel].
 *
 * Covers:
 * - `currentLocation` mirrors the persisted [PreferenceKeys.DEFAULT_LOCATION]
 * - `updateQuery` + debounce → `CitySearchRepository.search` called once
 *   per settled input (not once per keystroke)
 * - `applyLocation` writes the chosen label to DataStore
 * - Blank query resets results immediately without hitting the repository
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WeatherLocationSettingsViewModelTest {

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
    fun `currentLocation reflects stored preference`() = runTest {
        val flow = MutableStateFlow<String?>("Shibuya")
        val prefs = prefsReturning(flow)
        val repo = mockk<CitySearchRepository>()

        val vm = WeatherLocationSettingsViewModel(prefs, repo)

        vm.currentLocation.test {
            assertThat(awaitItem()).isEqualTo("")
            assertThat(awaitItem()).isEqualTo("Shibuya")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `currentLocation maps unset preference to empty`() = runTest {
        val prefs = prefsReturning(MutableStateFlow(null))
        val vm = WeatherLocationSettingsViewModel(prefs, mockk())

        vm.currentLocation.test {
            assertThat(awaitItem()).isEqualTo("")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateQuery debounces repeated keystrokes and calls search once`() = runTest {
        val prefs = prefsReturning(MutableStateFlow(null))
        val repo = mockk<CitySearchRepository>()
        coEvery { repo.search(any(), any()) } returns Result.success(
            listOf(
                suggestion("Tokyo", admin = "Tokyo", country = "Japan")
            )
        )
        val vm = WeatherLocationSettingsViewModel(prefs, repo)

        vm.updateQuery("T")
        vm.updateQuery("To")
        vm.updateQuery("Tok")
        vm.updateQuery("Toky")
        vm.updateQuery("Tokyo")

        // Each keystroke < DEBOUNCE_MS apart; only the final settled value
        // should trigger a single repository call.
        advanceTimeBy(100)
        coVerify(exactly = 0) { repo.search(any(), any()) }

        advanceTimeBy(500)
        coVerify(exactly = 1) { repo.search("Tokyo", any()) }
    }

    @Test
    fun `updateQuery with blank value clears results without repository call`() = runTest {
        val prefs = prefsReturning(MutableStateFlow(null))
        val repo = mockk<CitySearchRepository>()
        coEvery { repo.search(any(), any()) } returns Result.success(emptyList())
        val vm = WeatherLocationSettingsViewModel(prefs, repo)

        vm.queryResults.test {
            assertThat(awaitItem()).isEmpty() // initial
            vm.updateQuery("   ")
            advanceTimeBy(500)
            // queryResults stays empty; no repo call expected.
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { repo.search(any(), any()) }
    }

    @Test
    fun `queryResults emits search hits after debounce`() = runTest {
        val prefs = prefsReturning(MutableStateFlow(null))
        val repo = mockk<CitySearchRepository>()
        val hit = suggestion("宗像", admin = "Fukuoka", country = "Japan")
        coEvery { repo.search("宗像", any()) } returns Result.success(listOf(hit))
        val vm = WeatherLocationSettingsViewModel(prefs, repo)

        vm.queryResults.test {
            assertThat(awaitItem()).isEmpty()
            vm.updateQuery("宗像")
            advanceTimeBy(500)
            assertThat(awaitItem()).containsExactly(hit)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `queryResults falls back to empty list when search fails`() = runTest {
        val prefs = prefsReturning(MutableStateFlow(null))
        val repo = mockk<CitySearchRepository>()
        coEvery { repo.search(any(), any()) } returns Result.failure(RuntimeException("network"))
        val vm = WeatherLocationSettingsViewModel(prefs, repo)

        vm.queryResults.test {
            assertThat(awaitItem()).isEmpty()
            vm.updateQuery("Nowhere")
            advanceTimeBy(500)
            // No emission on failure; the StateFlow stays at its initial empty.
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `applyLocation persists the chosen label`() = runTest {
        val prefs = mockk<AppPreferences>()
        every { prefs.observe(PreferenceKeys.DEFAULT_LOCATION) } returns MutableStateFlow(null)
        val keySlot = slot<Preferences.Key<String>>()
        val valueSlot = slot<String>()
        coEvery { prefs.set(capture(keySlot), capture(valueSlot)) } just Runs

        val vm = WeatherLocationSettingsViewModel(prefs, mockk())

        vm.applyLocation("宗像, Fukuoka, Japan")
        advanceUntilIdle()

        coVerify(exactly = 1) { prefs.set(PreferenceKeys.DEFAULT_LOCATION, "宗像, Fukuoka, Japan") }
    }

    @Test
    fun `applyLocation trims whitespace`() = runTest {
        val prefs = mockk<AppPreferences>()
        every { prefs.observe(PreferenceKeys.DEFAULT_LOCATION) } returns MutableStateFlow(null)
        coEvery { prefs.set(any<Preferences.Key<String>>(), any<String>()) } just Runs
        val vm = WeatherLocationSettingsViewModel(prefs, mockk())

        vm.applyLocation("  Tokyo  ")
        advanceUntilIdle()

        coVerify(exactly = 1) { prefs.set(PreferenceKeys.DEFAULT_LOCATION, "Tokyo") }
    }

    private fun suggestion(
        name: String,
        admin: String?,
        country: String
    ): CitySuggestion = CitySuggestion(
        name = name,
        admin1 = admin,
        country = country,
        latitude = 0.0,
        longitude = 0.0,
        displayLabel = listOfNotNull(name, admin, country).joinToString(", ")
    )

    private fun prefsReturning(flow: Flow<String?>): AppPreferences {
        val prefs = mockk<AppPreferences>(relaxed = true)
        every { prefs.observe(PreferenceKeys.DEFAULT_LOCATION) } returns flow
        every { prefs.observe<Any>(any<Preferences.Key<Any>>()) } answers {
            @Suppress("UNCHECKED_CAST")
            flow as Flow<Any?>
        }
        return prefs
    }
}
