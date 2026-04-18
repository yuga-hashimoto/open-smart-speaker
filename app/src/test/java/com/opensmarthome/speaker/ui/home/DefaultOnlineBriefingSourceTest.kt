package com.opensmarthome.speaker.ui.home

import com.google.common.truth.Truth.assertThat
import com.opensmarthome.speaker.data.preferences.AppPreferences
import com.opensmarthome.speaker.data.preferences.PreferenceKeys
import com.opensmarthome.speaker.tool.info.DayForecast
import com.opensmarthome.speaker.tool.info.NewsItem
import com.opensmarthome.speaker.tool.info.NewsProvider
import com.opensmarthome.speaker.tool.info.WeatherInfo
import com.opensmarthome.speaker.tool.info.WeatherProvider
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultOnlineBriefingSourceTest {

    private class FakeWeatherProvider(
        private val result: WeatherInfo? = null,
        private val error: Throwable? = null,
    ) : WeatherProvider {
        var lastLocation: String? = null
            private set
        var lastLocationWasExplicit = false
            private set
        var calls = 0
            private set

        override suspend fun getCurrent(location: String?): WeatherInfo {
            calls++
            lastLocation = location
            lastLocationWasExplicit = location != null
            error?.let { throw it }
            return requireNotNull(result) { "FakeWeatherProvider configured with no result and no error" }
        }

        override suspend fun getForecast(location: String?, days: Int): List<DayForecast> = emptyList()
    }

    private class FakeNewsProvider(
        private val result: List<NewsItem> = emptyList(),
        private val error: Throwable? = null,
    ) : NewsProvider {
        var lastFeedUrl: String? = null
            private set
        var lastLimit: Int = -1
            private set

        override suspend fun getHeadlines(feedUrl: String, limit: Int): List<NewsItem> {
            lastFeedUrl = feedUrl
            lastLimit = limit
            error?.let { throw it }
            return result
        }
    }

    private fun prefsReturning(location: String?): AppPreferences {
        val prefs = mockk<AppPreferences>(relaxed = true)
        every { prefs.observe(PreferenceKeys.DEFAULT_LOCATION) } returns flowOf(location)
        return prefs
    }

    @Test
    fun `currentWeather uses DEFAULT_LOCATION preference when set`() = runTest {
        val weather = FakeWeatherProvider(result = WeatherInfo("Osaka", 22.0, "Clear", 50, 8.0))
        val news = FakeNewsProvider()
        val source = DefaultOnlineBriefingSource(weather, news, prefsReturning("Osaka"))

        val result = source.currentWeather()

        assertThat(result.isSuccess).isTrue()
        val info = result.getOrNull()
        assertThat(info).isNotNull()
        assertThat(info!!.location).isEqualTo("Osaka")
        assertThat(weather.lastLocation).isEqualTo("Osaka")
    }

    @Test
    fun `currentWeather trims whitespace from preference value`() = runTest {
        val weather = FakeWeatherProvider(result = WeatherInfo("Kyoto", 20.0, "Cloudy", 60, 5.0))
        val source = DefaultOnlineBriefingSource(weather, FakeNewsProvider(), prefsReturning("  Kyoto  "))

        source.currentWeather()

        assertThat(weather.lastLocation).isEqualTo("Kyoto")
    }

    @Test
    fun `currentWeather passes null when preference is blank`() = runTest {
        val weather = FakeWeatherProvider(result = WeatherInfo("Tokyo", 15.0, "Rain", 80, 12.0))
        val source = DefaultOnlineBriefingSource(weather, FakeNewsProvider(), prefsReturning("   "))

        source.currentWeather()

        assertThat(weather.lastLocation).isNull()
    }

    @Test
    fun `currentWeather returns Result failure when provider throws generic exception`() = runTest {
        val weather = FakeWeatherProvider(error = RuntimeException("geocode down"))
        val source = DefaultOnlineBriefingSource(weather, FakeNewsProvider(), prefsReturning("Osaka"))

        val result = source.currentWeather()

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(RuntimeException::class.java)
    }

    @Test
    fun `currentWeather returns Result failure preserving IOException for network errors`() = runTest {
        // IOException must propagate unwrapped so HomeViewModel's classify()
        // can bucket it as BriefingState.Error.Kind.Network.
        val weather = FakeWeatherProvider(error = IOException("offline"))
        val source = DefaultOnlineBriefingSource(weather, FakeNewsProvider(), prefsReturning("Osaka"))

        val result = source.currentWeather()

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(IOException::class.java)
    }

    @Test
    fun `latestHeadlines returns Result success with items from news provider`() = runTest {
        val news = FakeNewsProvider(
            result = listOf(
                NewsItem("headline 1", "summary 1", "https://example/1", "2026-04-17T08:00"),
                NewsItem("headline 2", "summary 2", "https://example/2", "2026-04-17T09:00"),
            )
        )
        val source = DefaultOnlineBriefingSource(FakeWeatherProvider(), news, prefsReturning(null))

        val result = source.latestHeadlines(limit = 2)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).hasSize(2)
        assertThat(news.lastFeedUrl).isEqualTo(DefaultOnlineBriefingSource.DEFAULT_FEED)
        assertThat(news.lastLimit).isEqualTo(2)
    }

    @Test
    fun `latestHeadlines coerces limit into 1 to 10 range`() = runTest {
        val news = FakeNewsProvider(result = emptyList())
        val source = DefaultOnlineBriefingSource(FakeWeatherProvider(), news, prefsReturning(null))

        source.latestHeadlines(limit = 0)
        assertThat(news.lastLimit).isEqualTo(1)

        source.latestHeadlines(limit = 100)
        assertThat(news.lastLimit).isEqualTo(10)
    }

    @Test
    fun `latestHeadlines returns Result failure when provider throws`() = runTest {
        val news = FakeNewsProvider(error = RuntimeException("feed down"))
        val source = DefaultOnlineBriefingSource(FakeWeatherProvider(), news, prefsReturning(null))

        val result = source.latestHeadlines()

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(RuntimeException::class.java)
    }

    @Test
    fun `latestHeadlines preserves IOException for network classification`() = runTest {
        val news = FakeNewsProvider(error = IOException("feed unreachable"))
        val source = DefaultOnlineBriefingSource(FakeWeatherProvider(), news, prefsReturning(null))

        val result = source.latestHeadlines()

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(IOException::class.java)
    }

    @Test
    fun `Empty source returns Result success with null weather and empty headlines`() = runTest {
        val source = OnlineBriefingSource.Empty
        val weather = source.currentWeather()
        val headlines = source.latestHeadlines()

        assertThat(weather.isSuccess).isTrue()
        assertThat(weather.getOrNull()).isNull()
        assertThat(headlines.isSuccess).isTrue()
        assertThat(headlines.getOrNull()).isEmpty()
    }
}
