package com.opendash.app

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Custom test runner that boots [HiltTestApplication] instead of [OpenDashApp].
 *
 * `@HiltAndroidTest` annotated tests need this so Hilt can inject test
 * doubles into the dependency graph. Without it, the production
 * `@HiltAndroidApp` Application would still be used and the test graph
 * wouldn't be active.
 *
 * Wired in via `defaultConfig.testInstrumentationRunner` in app/build.gradle.kts.
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?
    ): Application {
        return super.newApplication(cl, HiltTestApplication::class.java.name, context)
    }
}
