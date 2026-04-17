package com.opensmarthome.speaker.ui.home

import com.google.common.truth.Truth.assertThat
import com.opensmarthome.speaker.device.DeviceManager
import com.opensmarthome.speaker.device.model.CommandResult
import com.opensmarthome.speaker.tool.info.NewsItem
import com.opensmarthome.speaker.tool.info.WeatherInfo
import com.opensmarthome.speaker.tool.system.TimerManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Focused tests for the online-briefing StateFlows (`onlineWeather`,
 * `headlines`). Kept separate from [HomeViewModelTest] because the
 * virtual-time wiring needed for `WhileSubscribed(5_000)` verification
 * would otherwise clutter the media-control and timer tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelBriefingTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setup() { Dispatchers.setMain(testDispatcher) }

    @AfterEach
    fun teardown() { Dispatchers.resetMain() }

    private class FakeBriefingSource(
        private val weather: WeatherInfo? = null,
        private val headlines: List<NewsItem> = emptyList(),
    ) : OnlineBriefingSource {
        var weatherCalls = 0
            private set
        var headlineCalls = 0
            private set

        override suspend fun currentWeather(): WeatherInfo? {
            weatherCalls++
            return weather
        }

        override suspend fun latestHeadlines(limit: Int): List<NewsItem> {
            headlineCalls++
            return headlines
        }
    }

    private fun stubbedTimerManager(): TimerManager = mockk<TimerManager>().apply {
        coEvery { getActiveTimers() } returns emptyList()
    }

    private fun newVm(briefing: OnlineBriefingSource): HomeViewModel {
        val deviceManager = mockk<DeviceManager>()
        every { deviceManager.devices } returns MutableStateFlow(emptyMap())
        coEvery { deviceManager.executeCommand(any()) } returns CommandResult(success = true)
        val ss = mockk<com.opensmarthome.speaker.assistant.proactive.SuggestionState>(relaxed = true)
        every { ss.current } returns MutableStateFlow(emptyList())
        val te = mockk<com.opensmarthome.speaker.tool.ToolExecutor>(relaxed = true)
        val bm = mockk<com.opensmarthome.speaker.util.BatteryMonitor>().apply {
            every { status } returns MutableStateFlow(
                com.opensmarthome.speaker.util.BatteryStatus(level = 80, isCharging = true)
            )
        }
        val tm = mockk<com.opensmarthome.speaker.util.ThermalMonitor>().apply {
            every { status } returns MutableStateFlow(com.opensmarthome.speaker.util.ThermalLevel.NORMAL)
        }
        return HomeViewModel(deviceManager, ss, te, stubbedTimerManager(), briefing, bm, tm)
    }

    @Test
    fun `onlineWeather emits value from briefing source once subscribed`() = runTest {
        val briefing = FakeBriefingSource(weather = WeatherInfo("Osaka", 22.0, "Clear", 55, 9.0))
        val vm = newVm(briefing)

        assertThat(vm.onlineWeather.value).isNull()
        val job = vm.onlineWeather.onEach { /* drain */ }.launchIn(backgroundScope)
        // One iteration is enough to observe the first emission; avoid
        // advanceUntilIdle() because the while(true)+delay(15min) loop
        // never goes idle in virtual time.
        advanceTimeBy(50L)

        val observed = vm.onlineWeather.value
        assertThat(observed).isNotNull()
        assertThat(observed!!.location).isEqualTo("Osaka")
        assertThat(briefing.weatherCalls).isAtLeast(1)
        job.cancel()
    }

    @Test
    fun `headlines emits value from briefing source once subscribed`() = runTest {
        val items = listOf(
            NewsItem("Alpha", "", "", ""),
            NewsItem("Beta", "", "", ""),
            NewsItem("Gamma", "", "", ""),
        )
        val briefing = FakeBriefingSource(headlines = items)
        val vm = newVm(briefing)

        assertThat(vm.headlines.value).isEmpty()
        val job = vm.headlines.onEach { /* drain */ }.launchIn(backgroundScope)
        advanceTimeBy(50L)

        assertThat(vm.headlines.value).containsExactlyElementsIn(items).inOrder()
        assertThat(briefing.headlineCalls).isAtLeast(1)
        job.cancel()
    }

    @Test
    fun `onlineWeather does not clobber last value on null emission`() = runTest {
        val steps = mutableListOf<WeatherInfo?>(
            WeatherInfo("Osaka", 22.0, "Clear", 55, 9.0),
            null,
            null,
        )
        val briefing = object : OnlineBriefingSource {
            override suspend fun currentWeather(): WeatherInfo? = steps.removeAt(0)
            override suspend fun latestHeadlines(limit: Int) = emptyList<NewsItem>()
        }
        val vm = newVm(briefing)
        val job = vm.onlineWeather.onEach { /* drain */ }.launchIn(backgroundScope)
        // First tick: populated. We only need to get past the initial loop body.
        advanceTimeBy(50L)
        val firstValue = vm.onlineWeather.value
        // Advance past the refresh interval so the loop re-enters. Subsequent
        // currentWeather() returns null — we expect value() to stay on Osaka.
        advanceTimeBy(HomeViewModel.WEATHER_REFRESH_MS + 50L)
        assertThat(vm.onlineWeather.value).isEqualTo(firstValue)
        assertThat(firstValue).isNotNull()
        job.cancel()
    }

    @Test
    fun `headlines does not clobber last value on empty emission`() = runTest {
        val first = listOf(NewsItem("Alpha", "", "", ""))
        val steps: MutableList<List<NewsItem>> = mutableListOf(first, emptyList(), emptyList())
        val briefing = object : OnlineBriefingSource {
            override suspend fun currentWeather(): WeatherInfo? = null
            override suspend fun latestHeadlines(limit: Int): List<NewsItem> = steps.removeAt(0)
        }
        val vm = newVm(briefing)
        val job = vm.headlines.onEach { /* drain */ }.launchIn(backgroundScope)
        advanceTimeBy(50L)
        val afterFirst = vm.headlines.value
        advanceTimeBy(HomeViewModel.HEADLINES_REFRESH_MS + 50L)
        assertThat(vm.headlines.value).isEqualTo(afterFirst)
        assertThat(afterFirst).containsExactlyElementsIn(first)
        job.cancel()
    }
}
