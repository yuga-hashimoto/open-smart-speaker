package com.opendash.app.ui.settings.locale

import androidx.datastore.preferences.core.Preferences
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.opendash.app.data.preferences.AppPreferences
import com.opendash.app.data.preferences.PreferenceKeys
import com.opendash.app.util.LocaleManager
import io.mockk.coEvery
import io.mockk.coVerify
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
 * Pure-JVM tests for the Language picker backing ViewModel. The real
 * [LocaleManager] push through the platform `android.app.LocaleManager`
 * is covered by instrumented tests; here we mock the manager and focus
 * on: (a) the current-tag Flow reflects the stored preference, and
 * (b) applyLocale delegates to LocaleManager.apply exactly once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LocaleSettingsViewModelTest {

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
    fun `currentTag reflects stored preference`() = runTest {
        val tagFlow = MutableStateFlow<String?>("ja")
        val prefs = prefsReturning(tagFlow)
        val locale = fakeLocaleManager(supported = true)

        val vm = LocaleSettingsViewModel(locale, prefs)

        vm.currentTag.test {
            // initial value from stateIn, then the mapped upstream value
            assertThat(awaitItem()).isEqualTo("")
            assertThat(awaitItem()).isEqualTo("ja")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `currentTag maps unset preference to empty string`() = runTest {
        val prefs = prefsReturning(MutableStateFlow(null))
        val vm = LocaleSettingsViewModel(fakeLocaleManager(supported = true), prefs)

        vm.currentTag.test {
            assertThat(awaitItem()).isEqualTo("")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `applyLocale delegates to LocaleManager apply`() = runTest {
        val locale = fakeLocaleManager(supported = true)
        val vm = LocaleSettingsViewModel(locale, prefsReturning(MutableStateFlow(null)))

        vm.applyLocale("fr")
        advanceUntilIdle()

        coVerify(exactly = 1) { locale.apply("fr") }
    }

    @Test
    fun `isOverrideSupported and options surface from LocaleManager`() = runTest {
        val locale = fakeLocaleManager(supported = false)
        val vm = LocaleSettingsViewModel(locale, prefsReturning(MutableStateFlow(null)))

        assertThat(vm.isOverrideSupported).isFalse()
        assertThat(vm.options.map { it.tag })
            .containsExactly("", "en", "ja", "es", "fr", "de", "zh-CN")
            .inOrder()
    }

    private fun fakeLocaleManager(supported: Boolean): LocaleManager {
        val mgr = mockk<LocaleManager>(relaxed = true)
        every { mgr.isOverrideSupported } returns supported
        every { mgr.options } returns listOf(
            LocaleManager.Option("", "System default"),
            LocaleManager.Option("en", "English"),
            LocaleManager.Option("ja", "日本語"),
            LocaleManager.Option("es", "Español"),
            LocaleManager.Option("fr", "Français"),
            LocaleManager.Option("de", "Deutsch"),
            LocaleManager.Option("zh-CN", "简体中文")
        )
        coEvery { mgr.apply(any()) } returns Unit
        return mgr
    }

    private fun prefsReturning(flow: Flow<String?>): AppPreferences {
        val prefs = mockk<AppPreferences>()
        every { prefs.observe(PreferenceKeys.APP_LOCALE_TAG) } returns flow
        every { prefs.observe<Any>(any<Preferences.Key<Any>>()) } answers {
            @Suppress("UNCHECKED_CAST")
            flow as Flow<Any?>
        }
        return prefs
    }
}
