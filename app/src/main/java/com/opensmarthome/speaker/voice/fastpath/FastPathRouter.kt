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
 *
 * Ambiguous information-seeking utterances ("トマトって何？", "explain
 * quantum computing", "tell me about kotlin") are pre-filtered out via
 * [AgentIntentHint] so the LLM can orchestrate multi-tool reasoning
 * instead of being pre-empted by a fast-path matcher. Explicit tool-verb
 * utterances (timer, weather, 検索, 予報…) still hit the fast path.
 */
class DefaultFastPathRouter(
    private val matchers: List<FastPathMatcher> = DEFAULT_MATCHERS
) : FastPathRouter {

    override fun match(utterance: String): FastPathMatch? {
        val normalized = utterance.trim().lowercase()
        if (normalized.isEmpty()) return null
        // Hand ambiguous info queries to the LLM so it can chain tools.
        if (AgentIntentHint.isAmbiguousInformationQuery(utterance)) return null
        for (m in matchers) {
            val match = m.tryMatch(normalized)
            if (match != null) return match
        }
        return null
    }

    companion object {
        val DEFAULT_MATCHERS: List<FastPathMatcher> = listOf(
            // BroadcastCancelTimerMatcher must precede CancelAllTimersMatcher:
            // its patterns are a scoped superset ("cancel timers on all speakers"
            // contains "cancel timers"), so the scoped multi-room variant has to
            // win whenever the qualifier is present.
            BroadcastCancelTimerMatcher,
            // CancelAllTimersMatcher must precede TimerMatcher because "cancel timer" contains "timer".
            CancelAllTimersMatcher,
            // BroadcastTimerMatcher must precede TimerMatcher: the JA variant
            // "全スピーカーで5分タイマー" contains "5分タイマー" which
            // TimerMatcher's JA regex would otherwise eat (routing to the local
            // set_timer instead of the cross-speaker broadcast_timer).
            BroadcastTimerMatcher,
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
            // SettingsMatcher must precede LaunchAppMatcher — "open wifi settings"
            // would otherwise be eaten as a launch_app("wifi settings") call.
            SettingsMatcher,
            // OpenUrlMatcher must precede LaunchAppMatcher so "open example.com"
            // and "open https://..." route to open_url instead of launch_app.
            OpenUrlMatcher,
            // HandoffMatcher owns "move this to <peer>" / "send to <peer>" /
            // "キッチンにハンドオフ" — narrow enough that it doesn't swallow
            // smart-home move verbs, but still before LaunchAppMatcher.
            HandoffMatcher,
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
            // WebSearchMatcher must sit after the weather/news/briefing
            // matchers so "search the weather" doesn't win over
            // WeatherMatcher, and before DatetimeMatcher/GreetingMatcher so
            // "look up the time" style wordings still route to web search
            // when the utterance isn't a plain time query.
            WebSearchMatcher,
            CalendarMatcher,
            // Notification matchers: clear precedes list because "clear" verb
            // dominates the "show / list" verbs on common notification utterances.
            ClearNotificationsMatcher,
            ListNotificationsMatcher,
            LocationMatcher,
            ListMemoryMatcher,
            ListDevicesMatcher,
            ListTimersMatcher,
            // DeviceHealthMatcher must precede DatetimeMatcher / GreetingMatcher /
            // HelpMatcher so "system status", "診断", "storage space" don't fall
            // through to pleasantries. Patterns are scoped to device/system/
            // storage/memory terms so unrelated utterances still pass through.
            DeviceHealthMatcher,
            // LockScreenMatcher before Datetime/Greeting/Help so "lock the
            // screen" doesn't get eaten; patterns are narrow (screen/lock/
            // tablet tokens) so unrelated utterances pass through.
            LockScreenMatcher,
            // ListPeersMatcher before Broadcast*Matcher so "list nearby
            // speakers" isn't captured by a broader broadcast pattern.
            ListPeersMatcher,
            // BroadcastGroupMatcher must precede BroadcastTtsMatcher so
            // "broadcast X to the kitchen" routes via the group tool path
            // instead of being swallowed as "broadcast to everyone".
            BroadcastGroupMatcher,
            // BroadcastTtsMatcher before DatetimeMatcher. Patterns start with
            // "broadcast"/"announce"/"tell all speakers"/"全スピーカーに…" so
            // they're disjoint from every earlier matcher.
            BroadcastTtsMatcher,
            // FlipCoinMatcher before DatetimeMatcher/Greeting/Help so
            // "flip a coin" / "コイン投げて" don't fall through to pleasantries.
            FlipCoinMatcher,
            // PickRandomMatcher before DatetimeMatcher/GreetingMatcher.
            // Patterns are anchored on explicit "pick/choose/select …
            // of/from/between" verbs or the JA "…から選んで" suffix, so
            // unrelated "pick up the phone" / "時間を選んで" cases still
            // fall through when they don't match the full shape.
            PickRandomMatcher,
            // LocaleSwitchMatcher before DatetimeMatcher/Greeting/Help —
            // patterns are scoped to "switch/change/set (the) language|locale|ui
            // to <name>" / "<name>にして" with a strict language-name whitelist,
            // so "change the lights" / "6時にして" still fall through.
            LocaleSwitchMatcher,
            DatetimeMatcher,
            GreetingMatcher,
            // RollDiceMatcher uses matchEntire anchors so it's safe this low —
            // any earlier, more permissive matcher would already have bitten.
            // Placed before HelpMatcher because "help" is a containsMatchIn
            // pattern on "what can you do" and doesn't overlap dice.
            RollDiceMatcher,
            HelpMatcher
        )
    }
}
