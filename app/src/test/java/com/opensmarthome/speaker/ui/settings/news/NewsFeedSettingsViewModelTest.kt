package com.opensmarthome.speaker.ui.settings.news

import androidx.datastore.preferences.core.Preferences
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.opensmarthome.speaker.data.preferences.AppPreferences
import com.opensmarthome.speaker.data.preferences.PreferenceKeys
import com.opensmarthome.speaker.tool.info.BundledNewsFeeds
import io.mockk.coVerify
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Pure-JVM tests for the News feed picker ViewModel. Mirrors the
 * structure of LocaleSettingsViewModelTest — mocks [AppPreferences],
 * runs the StateFlow through Turbine, and verifies that mutation
 * methods persist both the URL and the human label.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NewsFeedSettingsViewModelTest {

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
    fun `currentFeedUrl reflects stored preference`() = runTest {
        val urlFlow = MutableStateFlow<String?>("https://example.com/rss")
        val labelFlow = MutableStateFlow<String?>("Example")
        val prefs = prefsReturning(urlFlow, labelFlow)

        val vm = NewsFeedSettingsViewModel(prefs)

        vm.currentFeedUrl.test {
            assertThat(awaitItem()).isEqualTo("")
            assertThat(awaitItem()).isEqualTo("https://example.com/rss")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `currentLabel reflects stored preference`() = runTest {
        val urlFlow = MutableStateFlow<String?>("https://example.com/rss")
        val labelFlow = MutableStateFlow<String?>("Example label")
        val prefs = prefsReturning(urlFlow, labelFlow)

        val vm = NewsFeedSettingsViewModel(prefs)

        vm.currentLabel.test {
            assertThat(awaitItem()).isEqualTo("")
            assertThat(awaitItem()).isEqualTo("Example label")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `currentFeedUrl maps unset preference to empty string`() = runTest {
        val prefs = prefsReturning(MutableStateFlow(null), MutableStateFlow(null))
        val vm = NewsFeedSettingsViewModel(prefs)

        vm.currentFeedUrl.test {
            assertThat(awaitItem()).isEqualTo("")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `bundled surfaces the full catalog`() = runTest {
        val prefs = prefsReturning(MutableStateFlow(null), MutableStateFlow(null))
        val vm = NewsFeedSettingsViewModel(prefs)

        assertThat(vm.bundled).isEqualTo(BundledNewsFeeds.ALL)
    }

    @Test
    fun `applyBundled persists url and label`() = runTest {
        val prefs = prefsReturning(MutableStateFlow(null), MutableStateFlow(null))
        val vm = NewsFeedSettingsViewModel(prefs)

        vm.applyBundled(BundledNewsFeeds.NHK_SOCIETY, "NHK 社会")
        advanceUntilIdle()

        coVerify(exactly = 1) {
            prefs.set(PreferenceKeys.DEFAULT_NEWS_FEED_URL, BundledNewsFeeds.NHK_SOCIETY.url)
        }
        coVerify(exactly = 1) {
            prefs.set(PreferenceKeys.DEFAULT_NEWS_FEED_LABEL, "NHK 社会")
        }
    }

    @Test
    fun `applyCustom trims whitespace and persists`() = runTest {
        val prefs = prefsReturning(MutableStateFlow(null), MutableStateFlow(null))
        val vm = NewsFeedSettingsViewModel(prefs)

        vm.applyCustom("  https://example.com/custom.rss  ", "  My feed  ")
        advanceUntilIdle()

        coVerify(exactly = 1) {
            prefs.set(PreferenceKeys.DEFAULT_NEWS_FEED_URL, "https://example.com/custom.rss")
        }
        coVerify(exactly = 1) {
            prefs.set(PreferenceKeys.DEFAULT_NEWS_FEED_LABEL, "My feed")
        }
    }

    @Test
    fun `resetToDefault removes both keys`() = runTest {
        val prefs = prefsReturning(MutableStateFlow(null), MutableStateFlow(null))
        val vm = NewsFeedSettingsViewModel(prefs)

        vm.resetToDefault()
        advanceUntilIdle()

        coVerify(exactly = 1) { prefs.remove(PreferenceKeys.DEFAULT_NEWS_FEED_URL) }
        coVerify(exactly = 1) { prefs.remove(PreferenceKeys.DEFAULT_NEWS_FEED_LABEL) }
    }

    private fun prefsReturning(
        urlFlow: Flow<String?>,
        labelFlow: Flow<String?>
    ): AppPreferences {
        val prefs = mockk<AppPreferences>(relaxed = true)
        // Stub each key explicitly so the ViewModel reads the right
        // upstream for each StateFlow. MockK resolves per-argument stubs
        // before the generic `any()` fallback, so ordering doesn't matter
        // here, but keeping them explicit documents the contract.
        every { prefs.observe(PreferenceKeys.DEFAULT_NEWS_FEED_URL) } returns urlFlow
        every { prefs.observe(PreferenceKeys.DEFAULT_NEWS_FEED_LABEL) } returns labelFlow
        coEvery { prefs.set(any<Preferences.Key<Any>>(), any()) } returns Unit
        coEvery { prefs.remove(any<Preferences.Key<Any>>()) } returns Unit
        return prefs
    }
}
