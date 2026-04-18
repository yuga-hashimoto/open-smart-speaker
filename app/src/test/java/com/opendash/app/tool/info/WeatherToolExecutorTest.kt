package com.opendash.app.tool.info

import com.google.common.truth.Truth.assertThat
import com.opendash.app.data.preferences.AppPreferences
import com.opendash.app.data.preferences.PreferenceKeys
import com.opendash.app.tool.ToolCall
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class WeatherToolExecutorTest {

    private lateinit var executor: WeatherToolExecutor
    private lateinit var weatherProvider: WeatherProvider

    private fun fakePrefs(defaultLocation: String?): AppPreferences {
        val prefs = mockk<AppPreferences>()
        every { prefs.observe(PreferenceKeys.DEFAULT_LOCATION) } returns flowOf(defaultLocation)
        return prefs
    }

    @BeforeEach
    fun setup() {
        weatherProvider = mockk(relaxed = true)
        executor = WeatherToolExecutor(weatherProvider)
    }

    @Test
    fun `availableTools returns weather tools`() = runTest {
        val tools = executor.availableTools()
        val names = tools.map { it.name }

        assertThat(names).contains("get_weather")
        assertThat(names).contains("get_forecast")
    }

    @Test
    fun `get_weather returns current conditions`() = runTest {
        coEvery { weatherProvider.getCurrent("Tokyo") } returns WeatherInfo(
            location = "Tokyo",
            temperatureC = 22.5,
            condition = "Partly cloudy",
            humidity = 60,
            windKph = 10.0
        )

        val result = executor.execute(
            ToolCall(id = "1", name = "get_weather", arguments = mapOf(
                "location" to "Tokyo"
            ))
        )

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("Tokyo")
        assertThat(result.data).contains("22.5")
        assertThat(result.data).contains("Partly cloudy")
    }

    @Test
    fun `get_weather without location uses default`() = runTest {
        coEvery { weatherProvider.getCurrent(null) } returns WeatherInfo(
            location = "Current location",
            temperatureC = 15.0,
            condition = "Clear",
            humidity = 50,
            windKph = 5.0
        )

        val result = executor.execute(
            ToolCall(id = "2", name = "get_weather", arguments = emptyMap())
        )

        assertThat(result.success).isTrue()
    }

    @Test
    fun `get_forecast returns multiple days`() = runTest {
        coEvery { weatherProvider.getForecast("Tokyo", 3) } returns listOf(
            DayForecast("2026-04-16", 15.0, 25.0, "Sunny"),
            DayForecast("2026-04-17", 12.0, 20.0, "Cloudy"),
            DayForecast("2026-04-18", 10.0, 18.0, "Rain")
        )

        val result = executor.execute(
            ToolCall(id = "3", name = "get_forecast", arguments = mapOf(
                "location" to "Tokyo",
                "days" to 3.0
            ))
        )

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("Sunny")
        assertThat(result.data).contains("Cloudy")
        assertThat(result.data).contains("Rain")
    }

    @Test
    fun `weather error returns failure`() = runTest {
        coEvery { weatherProvider.getCurrent(any()) } throws RuntimeException("Network error")

        val result = executor.execute(
            ToolCall(id = "4", name = "get_weather", arguments = mapOf(
                "location" to "Tokyo"
            ))
        )

        assertThat(result.success).isFalse()
    }

    // --- Bug B: DEFAULT_LOCATION preference fallback ---

    @Test
    fun `empty location argument falls back to default location preference`() = runTest {
        val prefs = fakePrefs("Munakata")
        val exec = WeatherToolExecutor(weatherProvider, prefs)
        coEvery { weatherProvider.getCurrent("Munakata") } returns WeatherInfo(
            location = "Munakata",
            temperatureC = 18.0,
            condition = "Clear",
            humidity = 55,
            windKph = 8.0
        )

        val result = exec.execute(
            ToolCall(id = "10", name = "get_weather", arguments = emptyMap())
        )

        assertThat(result.success).isTrue()
        coVerify { weatherProvider.getCurrent("Munakata") }
    }

    @Test
    fun `blank default location falls through to provider null for backward compat`() = runTest {
        val prefs = fakePrefs("")
        val exec = WeatherToolExecutor(weatherProvider, prefs)
        coEvery { weatherProvider.getCurrent(null) } returns WeatherInfo(
            location = "Tokyo",
            temperatureC = 20.0,
            condition = "Clear",
            humidity = 50,
            windKph = 5.0
        )

        val result = exec.execute(
            ToolCall(id = "11", name = "get_weather", arguments = emptyMap())
        )

        assertThat(result.success).isTrue()
        coVerify { weatherProvider.getCurrent(null) }
    }

    @Test
    fun `explicit location argument wins over default preference`() = runTest {
        val prefs = fakePrefs("Munakata")
        val exec = WeatherToolExecutor(weatherProvider, prefs)
        coEvery { weatherProvider.getCurrent("Paris") } returns WeatherInfo(
            location = "Paris",
            temperatureC = 12.0,
            condition = "Rain",
            humidity = 80,
            windKph = 15.0
        )

        val result = exec.execute(
            ToolCall(id = "12", name = "get_weather", arguments = mapOf("location" to "Paris"))
        )

        assertThat(result.success).isTrue()
        coVerify { weatherProvider.getCurrent("Paris") }
    }

    @Test
    fun `forecast also honours default location preference`() = runTest {
        val prefs = fakePrefs("Osaka")
        val exec = WeatherToolExecutor(weatherProvider, prefs)
        coEvery { weatherProvider.getForecast("Osaka", 3) } returns listOf(
            DayForecast("2026-04-16", 15.0, 22.0, "Sunny")
        )

        val result = exec.execute(
            ToolCall(id = "13", name = "get_forecast", arguments = emptyMap())
        )

        assertThat(result.success).isTrue()
        coVerify { weatherProvider.getForecast("Osaka", 3) }
    }
}
