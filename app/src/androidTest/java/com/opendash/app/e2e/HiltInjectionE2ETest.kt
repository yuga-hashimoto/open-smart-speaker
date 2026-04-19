package com.opendash.app.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.opendash.app.data.preferences.AppPreferences
import com.opendash.app.data.preferences.PreferenceKeys
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Smoke test for the Hilt-on-instrumented-runner pipeline.
 *
 * Demonstrates that `@HiltAndroidTest` boots the real DI graph against
 * [com.opendash.app.HiltTestRunner] / `HiltTestApplication`, and that
 * we can resolve a real `@Singleton` (`AppPreferences`) backed by a
 * real `DataStore`. Future E2E tests can layer on @BindValue / @TestInstallIn
 * to swap providers (Stt / Tts / Assistant) without changing this scaffolding.
 *
 * Writes use a unique key so they don't bleed into the production
 * preferences store on the test device.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class HiltInjectionE2ETest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var appPreferences: AppPreferences

    @Before
    fun injectDeps() {
        hiltRule.inject()
    }

    @Test
    fun preferences_round_trip_value() = runTest {
        val key = PreferenceKeys.WAKE_WORD_SENSITIVITY
        appPreferences.set(key, 0.42f)
        try {
            val read = appPreferences.observe(key).first()
            assertThat(read).isWithin(1e-6f).of(0.42f)
        } finally {
            appPreferences.remove(key)
        }
    }
}
