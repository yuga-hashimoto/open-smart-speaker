package com.opensmarthome.speaker.voice.fastpath

/**
 * Routes simple, high-frequency utterances directly to tool calls without
 * round-tripping through the LLM. Target latency <200ms from final STT
 * result to tool execution.
 *
 * This is the "Alexa feel" path — the LLM is still there for the long tail
 * of complex requests, but canonical smart-speaker commands fire instantly.
 *
 * Patterns are language-aware; default matchers cover English + Japanese.
 *
 * See [FastPathMatchers] for the bundled matcher implementations.
 */
interface FastPathRouter {
    fun match(utterance: String): FastPathMatch?
}

/**
 * Result of a fast-path lookup.
 *
 * @property toolName Tool to invoke, or null for speak-only responses (e.g. "help").
 * @property arguments Tool call arguments.
 * @property spokenConfirmation Short confirmation phrase the TTS can speak
 *     while/after the tool runs.
 */
data class FastPathMatch(
    val toolName: String?,
    val arguments: Map<String, Any?> = emptyMap(),
    val spokenConfirmation: String? = null
)

/** Implemented by every entry in [DefaultFastPathRouter.DEFAULT_MATCHERS]. */
interface FastPathMatcher {
    fun tryMatch(normalized: String): FastPathMatch?
}

/**
 * The shipped router: lowercases + trims input, then walks [matchers] in
 * order and returns the first hit. Order in [DEFAULT_MATCHERS] matters —
 * see comments inline for matchers that overlap.
 */
class DefaultFastPathRouter(
    private val matchers: List<FastPathMatcher> = DEFAULT_MATCHERS
) : FastPathRouter {

    override fun match(utterance: String): FastPathMatch? {
        val normalized = utterance.trim().lowercase()
        if (normalized.isEmpty()) return null
        for (m in matchers) {
            val match = m.tryMatch(normalized)
            if (match != null) return match
        }
        return null
    }

    companion object {
        val DEFAULT_MATCHERS: List<FastPathMatcher> = listOf(
            // CancelAllTimersMatcher must precede TimerMatcher because "cancel timer" contains "timer".
            CancelAllTimersMatcher,
            TimerMatcher,
            // AlarmMatcher must sit AFTER TimerMatcher — if the utterance
            // contains "timer" (duration-based) TimerMatcher wins; otherwise
            // wall-clock "set an alarm for 7am" / "wake me up at 6:30" / "7時にアラーム"
            // fall through to AlarmMatcher.
            AlarmMatcher,
            TimeQueryMatcher,
            VolumeMatcher,
            ThermostatMatcher,
            FanMatcher,
            TvMatcher,
            LockMatcher,
            // CoverMatcher must precede LaunchAppMatcher because "open the blinds"
            // would otherwise fall through to LaunchAppMatcher's guard.
            CoverMatcher,
            // EverythingOffMatcher must precede LightsMatcher because "lights off" partially overlaps "off".
            EverythingOffMatcher,
            LightsMatcher,
            MediaControlMatcher,
            // RunRoutineMatcher must precede LaunchAppMatcher because "run X" overlaps.
            RunRoutineMatcher,
            LaunchAppMatcher,
            FindDeviceMatcher,
            // GoodnightMatcher must precede the GreetingMatcher's "good night"
            // pleasantry rule so the destructive routine wins.
            GoodnightMatcher,
            ArriveHomeMatcher,
            LeaveHomeMatcher,
            // Briefing matchers must precede WeatherMatcher / NewsMatcher
            // so 'good morning briefing' / 'evening briefing' win.
            MorningBriefingMatcher,
            EveningBriefingMatcher,
            // ForecastMatcher must precede WeatherMatcher because both
            // contain "weather" / "天気"; forecast wants multi-day get_forecast.
            ForecastMatcher,
            WeatherMatcher,
            NewsMatcher,
            CalendarMatcher,
            // Notification matchers: clear precedes list because "clear" verb
            // dominates the "show / list" verbs on common notification utterances.
            ClearNotificationsMatcher,
            ListNotificationsMatcher,
            LocationMatcher,
            ListMemoryMatcher,
            ListDevicesMatcher,
            ListTimersMatcher,
            DatetimeMatcher,
            GreetingMatcher,
            HelpMatcher
        )
    }
}
