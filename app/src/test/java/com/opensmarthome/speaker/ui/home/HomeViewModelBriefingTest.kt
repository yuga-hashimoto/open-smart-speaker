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
import java.io.IOException
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
        private val weather: Result<WeatherInfo?> = Result.success(null),
        private val headlines: Result<List<NewsItem>> = Result.success(emptyList()),
    ) : OnlineBriefingSource {
        var weatherCalls = 0
            private set
        var headlineCalls = 0
            private set

        override suspend fun currentWeather(): Result<WeatherInfo?> {
            weatherCalls++
            return weather
        }

        override suspend fun latestHeadlines(limit: Int): Result<List<NewsItem>> {
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
        return HomeViewModel(deviceManager, ss, te, stubbedTimerManager(), briefing, bm, tm, UpcomingEventSource.Empty)
    }

    @Test
    fun `onlineWeather exposes Loading before first emission`() = runTest {
        val vm = newVm(FakeBriefingSource())
        assertThat(vm.onlineWeather.value).isEqualTo(BriefingState.Loading)
    }

    @Test
    fun `headlines exposes Loading before first emission`() = runTest {
        val vm = newVm(FakeBriefingSource())
        assertThat(vm.headlines.value).isEqualTo(BriefingState.Loading)
    }

    @Test
    fun `onlineWeather emits Success with value from briefing source once subscribed`() = runTest {
        val briefing = FakeBriefingSource(
            weather = Result.success(WeatherInfo("Osaka", 22.0, "Clear", 55, 9.0))
        )
        val vm = newVm(briefing)

        val job = vm.onlineWeather.onEach { /* drain */ }.launchIn(backgroundScope)
        // One iteration is enough to observe the first emission; avoid
        // advanceUntilIdle() because the while(true)+delay(15min) loop
        // never goes idle in virtual time.
        advanceTimeBy(50L)

        val observed = vm.onlineWeather.value
        assertThat(observed).isInstanceOf(BriefingState.Success::class.java)
        val success = observed as BriefingState.Success
        assertThat(success.data).isNotNull()
        assertThat(success.data!!.location).isEqualTo("Osaka")
        assertThat(briefing.weatherCalls).isAtLeast(1)
        job.cancel()
    }

    @Test
    fun `headlines emits Success with items from briefing source once subscribed`() = runTest {
        val items = listOf(
            NewsItem("Alpha", "", "", ""),
            NewsItem("Beta", "", "", ""),
            NewsItem("Gamma", "", "", ""),
        )
        val briefing = FakeBriefingSource(headlines = Result.success(items))
        val vm = newVm(briefing)

        val job = vm.headlines.onEach { /* drain */ }.launchIn(backgroundScope)
        advanceTimeBy(50L)

        val observed = vm.headlines.value
        assertThat(observed).isInstanceOf(BriefingState.Success::class.java)
        val success = observed as BriefingState.Success
        assertThat(success.data).containsExactlyElementsIn(items).inOrder()
        assertThat(briefing.headlineCalls).isAtLeast(1)
        job.cancel()
    }

    @Test
    fun `onlineWeather emits Network error when source returns IOException failure`() = runTest {
        val briefing = FakeBriefingSource(
            weather = Result.failure(IOException("offline"))
        )
        val vm = newVm(briefing)

        val job = vm.onlineWeather.onEach { /* drain */ }.launchIn(backgroundScope)
        advanceTimeBy(50L)

        val observed = vm.onlineWeather.value
        assertThat(observed).isInstanceOf(BriefingState.Error::class.java)
        val error = observed as BriefingState.Error
        assertThat(error.kind).isEqualTo(BriefingState.Error.Kind.Network)
        job.cancel()
    }

    @Test
    fun `headlines emits Network error when source returns IOException failure`() = runTest {
        val briefing = FakeBriefingSource(
            headlines = Result.failure(IOException("feed unreachable"))
        )
        val vm = newVm(briefing)

        val job = vm.headlines.onEach { /* drain */ }.launchIn(backgroundScope)
        advanceTimeBy(50L)

        val observed = vm.headlines.value
        assertThat(observed).isInstanceOf(BriefingState.Error::class.java)
        val error = observed as BriefingState.Error
        assertThat(error.kind).isEqualTo(BriefingState.Error.Kind.Network)
        job.cancel()
    }

    @Test
    fun `onlineWeather emits Parse error for IllegalStateException`() = runTest {
        val briefing = FakeBriefingSource(
            weather = Result.failure(IllegalStateException("bad body"))
        )
        val vm = newVm(briefing)

        val job = vm.onlineWeather.onEach { /* drain */ }.launchIn(backgroundScope)
        advanceTimeBy(50L)

        val observed = vm.onlineWeather.value
        assertThat(observed).isInstanceOf(BriefingState.Error::class.java)
        val error = observed as BriefingState.Error
        assertThat(error.kind).isEqualTo(BriefingState.Error.Kind.Parse)
        job.cancel()
    }

    @Test
    fun `onlineWeather emits Unknown error for generic RuntimeException`() = runTest {
        val briefing = FakeBriefingSource(
            weather = Result.failure(RuntimeException("???"))
        )
        val vm = newVm(briefing)

        val job = vm.onlineWeather.onEach { /* drain */ }.launchIn(backgroundScope)
        advanceTimeBy(50L)

        val observed = vm.onlineWeather.value
        assertThat(observed).isInstanceOf(BriefingState.Error::class.java)
        val error = observed as BriefingState.Error
        assertThat(error.kind).isEqualTo(BriefingState.Error.Kind.Unknown)
        job.cancel()
    }

    @Test
    fun `onlineWeather emits Success with null data when provider returns no location`() = runTest {
        // Success-with-null is a legitimate "provider resolved nothing" outcome
        // (e.g. empty geocoding result) and is kept separate from errors so the
        // UI can fall back to the sensor-based weather widget.
        val briefing = FakeBriefingSource(weather = Result.success(null))
        val vm = newVm(briefing)

        val job = vm.onlineWeather.onEach { /* drain */ }.launchIn(backgroundScope)
        advanceTimeBy(50L)

        val observed = vm.onlineWeather.value
        assertThat(observed).isInstanceOf(BriefingState.Success::class.java)
        assertThat((observed as BriefingState.Success).data).isNull()
        job.cancel()
    }
}
