package com.opensmarthome.speaker.voice.fastpath

/**
 * All bundled FastPathMatcher implementations.
 *
 * Each matcher is a stateless `object` with a single `tryMatch` entry point.
 * Order in [DefaultFastPathRouter.DEFAULT_MATCHERS] matters because some
 * matchers overlap (cancel-all-timers vs timer, run-routine vs launch-app, etc.).
 *
 * Adding a new matcher:
 * 1. Append the object below.
 * 2. Slot it into [DefaultFastPathRouter.DEFAULT_MATCHERS] respecting precedence.
 * 3. Add a test in FastPathRouterTest.
 */

/** "set timer 5 minutes", "5 分タイマー" */
object TimerMatcher : FastPathMatcher {
    private val englishRegex = Regex(
        """(?:set\s+)?timer\s*(?:for\s+)?(\d+)\s*(seconds?|minutes?|hours?|min|sec|hr)"""
    )
    private val japaneseRegex = Regex("""(\d+)\s*(秒|分|時間)\s*(タイマー|たいまー)?""")

    override fun tryMatch(normalized: String): FastPathMatch? {
        englishRegex.find(normalized)?.let {
            val n = it.groupValues[1].toInt()
            val unit = it.groupValues[2].lowercase()
            val seconds = toSeconds(n, unit) ?: return null
            return FastPathMatch(
                toolName = "set_timer",
                arguments = mapOf("seconds" to seconds.toDouble()),
                spokenConfirmation = "Timer set for $n ${unit.trimEnd('s')}${if (n != 1) "s" else ""}."
            )
        }
        japaneseRegex.find(normalized)?.let {
            val n = it.groupValues[1].toInt()
            val unit = it.groupValues[2]
            val seconds = when (unit) {
                "秒" -> n
                "分" -> n * 60
                "時間" -> n * 3600
                else -> return null
            }
            return FastPathMatch(
                toolName = "set_timer",
                arguments = mapOf("seconds" to seconds.toDouble()),
                spokenConfirmation = "${n}${unit}のタイマーを設定しました。"
            )
        }
        return null
    }

    private fun toSeconds(n: Int, unit: String): Int? = when {
        unit.startsWith("sec") || unit == "s" -> n
        unit.startsWith("min") -> n * 60
        unit.startsWith("hour") || unit == "hr" -> n * 3600
        else -> null
    }
}

/** "cancel all timers", "stop all timers", "タイマー全部止めて" */
object CancelAllTimersMatcher : FastPathMatcher {
    private val patterns = listOf(
        Regex("""(?:cancel|stop|clear)\s+(?:all\s+)?timers?"""),
        Regex("""タイマー\s*(?:を)?\s*(?:全部|全て)?\s*(?:止めて|キャンセル|やめて)""")
    )

    override fun tryMatch(normalized: String): FastPathMatch? {
        for (p in patterns) {
            if (p.containsMatchIn(normalized)) {
                return FastPathMatch(
                    toolName = "cancel_all_timers",
                    spokenConfirmation = "Timers cancelled."
                )
            }
        }
        return null
    }
}

/** "what time is it", "今何時" */
object TimeQueryMatcher : FastPathMatcher {
    private val patterns = listOf(
        Regex("""what\s+time\s+is\s+it"""),
        Regex("""what'?s\s+the\s+time"""),
        Regex("""今\s*何時""")
    )

    override fun tryMatch(normalized: String): FastPathMatch? {
        for (p in patterns) {
            if (p.containsMatchIn(normalized)) {
                return FastPathMatch(toolName = "get_datetime", arguments = emptyMap())
            }
        }
        return null
    }
}

/** "volume up", "louder", "音量上げて", "mute" */
object VolumeMatcher : FastPathMatcher {
    private val upPatterns = listOf(
        Regex("""(?:volume\s+up|louder|turn\s+(?:it\s+)?up)"""),
        Regex("""音量\s*(?:を\s*)?(?:上げて|大きく)""")
    )
    private val downPatterns = listOf(
        Regex("""(?:volume\s+down|quieter|turn\s+(?:it\s+)?down)"""),
        Regex("""音量\s*(?:を\s*)?(?:下げて|小さく)""")
    )
    private val mutePatterns = listOf(
        Regex("""^\s*mute\s*[!?.]*\s*$"""),
        Regex("""(?:be\s+)?(?:quiet|silent)"""),
        Regex("""ミュート"""),
        Regex("""(?:音|音量)\s*(?:を)?\s*(?:消して|オフ)""")
    )
    private val unmutePatterns = listOf(
        Regex("""^\s*unmute\s*[!?.]*\s*$"""),
        Regex("""ミュート\s*(?:を)?\s*(?:解除|オフ|やめて)""")
    )
    private val setPattern = Regex("""(?:set\s+)?volume\s+(?:to\s+)?(\d+)""")

    override fun tryMatch(normalized: String): FastPathMatch? {
        setPattern.find(normalized)?.let {
            val level = it.groupValues[1].toInt().coerceIn(0, 100)
            return FastPathMatch(toolName = "set_volume", arguments = mapOf("level" to level.toDouble()))
        }
        if (mutePatterns.any { it.containsMatchIn(normalized) }) {
            return FastPathMatch(toolName = "set_volume", arguments = mapOf("level" to 0.0), spokenConfirmation = "Muted.")
        }
        if (unmutePatterns.any { it.containsMatchIn(normalized) }) {
            return FastPathMatch(toolName = "set_volume", arguments = mapOf("level" to 50.0), spokenConfirmation = "Unmuted.")
        }
        if (upPatterns.any { it.containsMatchIn(normalized) }) {
            return FastPathMatch(toolName = "set_volume", arguments = mapOf("level" to 70.0), spokenConfirmation = "Volume up.")
        }
        if (downPatterns.any { it.containsMatchIn(normalized) }) {
            return FastPathMatch(toolName = "set_volume", arguments = mapOf("level" to 30.0), spokenConfirmation = "Volume down.")
        }
        return null
    }
}

/**
 * "turn off everything", "all off", "全部消して". Conservative — requires explicit
 * 'everything'/'all'/'全部' so casual 'turn it off' can't nuke the house.
 */
object EverythingOffMatcher : FastPathMatcher {
    private val patterns = listOf(
        Regex("""(?:turn\s+)?(?:off\s+)?(?:everything|all\s+(?:lights\s+and\s+switches|devices))(?:\s+off)?"""),
        Regex("""(?:全部|ぜんぶ|全て)\s*(?:を)?\s*(?:消して|オフ|切って)""")
    )

    override fun tryMatch(normalized: String): FastPathMatch? {
        for (p in patterns) {
            if (p.containsMatchIn(normalized)) {
                return FastPathMatch(
                    toolName = "execute_command",
                    arguments = mapOf("device_type" to "light", "action" to "turn_off"),
                    spokenConfirmation = "Everything off."
                )
            }
        }
        return null
    }
}

/** "set thermostat to 22", "エアコン22度に" */
object ThermostatMatcher : FastPathMatcher {
    private val englishRegex = Regex(
        """(?:set|change)\s+(?:the\s+)?(?:thermostat|ac|aircon|temperature)\s+(?:to\s+)?(\d{1,2})\s*(?:degrees?|°)?"""
    )
    private val japaneseRegex = Regex("""(?:エアコン|温度|設定温度)\s*(?:を)?\s*(\d{1,2})\s*(?:度|℃)""")

    override fun tryMatch(normalized: String): FastPathMatch? {
        val match = englishRegex.find(normalized) ?: japaneseRegex.find(normalized) ?: return null
        val temp = match.groupValues[1].toInt().coerceIn(10, 32)
        return FastPathMatch(
            toolName = "execute_command",
            arguments = mapOf(
                "device_type" to "climate",
                "action" to "set_temperature",
                "parameters" to mapOf("temperature" to temp)
            ),
            spokenConfirmation = "Setting to ${temp} degrees."
        )
    }
}

/** "lights on/off", "電気つけて/消して", "set brightness 50", "明るさ50%" */
object LightsMatcher : FastPathMatcher {
    private val onPatterns = listOf(
        Regex("""(?:turn\s+)?(?:the\s+)?lights?\s+on"""),
        Regex("""(?:電気|ライト|明かり)\s*(?:を\s*)?(?:つけて|点けて|オン)""")
    )
    private val offPatterns = listOf(
        Regex("""(?:turn\s+)?(?:the\s+)?lights?\s+off"""),
        Regex("""(?:電気|ライト|明かり)\s*(?:を\s*)?(?:消して|けして|オフ)""")
    )
    // Room-scoped: "turn off the bedroom lights", "kitchen lights on",
    // "turn the bedroom lights off"
    private val roomOffEnglishA = Regex("""turn\s+off\s+(?:the\s+)?(.+?)\s+lights?""")
    private val roomOffEnglishB = Regex("""(?:turn\s+(?:the\s+)?|)(.+?)\s+lights?\s+off""")
    private val roomOnEnglishA = Regex("""turn\s+on\s+(?:the\s+)?(.+?)\s+lights?""")
    private val roomOnEnglishB = Regex("""(?:turn\s+(?:the\s+)?|)(.+?)\s+lights?\s+on""")
    // Room-scoped JP: "寝室の電気消して" / "リビングの電気つけて"
    private val roomOffJp = Regex("""(.+?)\s*の\s*(?:電気|ライト|明かり)\s*(?:を)?\s*(?:消して|けして|オフ)""")
    private val roomOnJp = Regex("""(.+?)\s*の\s*(?:電気|ライト|明かり)\s*(?:を)?\s*(?:つけて|点けて|オン)""")
    private val brightnessSetEn = Regex(
        """(?:set\s+)?(?:lights?\s+)?(?:to\s+)?(\d{1,3})\s*(?:%|percent)\s*(?:brightness)?"""
    )
    private val brightnessSetJa = Regex("""明るさ\s*(?:を)?\s*(\d{1,3})\s*(?:%|パーセント)?""")
    private val dimPatterns = listOf(
        Regex("""dim\s+(?:the\s+)?lights?"""),
        Regex("""(?:電気|ライト|明かり)\s*(?:を)?\s*(?:暗く|くらく)""")
    )
    private val brighterPatterns = listOf(
        Regex("""brighten\s+(?:the\s+)?lights?"""),
        Regex("""(?:make\s+(?:the\s+)?lights?\s+brighter)"""),
        Regex("""(?:電気|ライト|明かり)\s*(?:を)?\s*(?:明るく|あかるく)""")
    )

    override fun tryMatch(normalized: String): FastPathMatch? {
        // Room-scoped EN: "turn off the bedroom lights" / "bedroom lights off".
        // Must precede unscoped patterns since they can also match.
        val trimmed = normalized.trim()
        sequenceOf(roomOffEnglishA, roomOffEnglishB)
            .mapNotNull { it.matchEntire(trimmed) }
            .firstOrNull()?.let {
                val room = it.groupValues[1].trim().removePrefix("the ")
                if (room.isNotEmpty() && room != "the") return roomLightsMatch(room, on = false)
            }
        sequenceOf(roomOnEnglishA, roomOnEnglishB)
            .mapNotNull { it.matchEntire(trimmed) }
            .firstOrNull()?.let {
                val room = it.groupValues[1].trim().removePrefix("the ")
                if (room.isNotEmpty() && room != "the") return roomLightsMatch(room, on = true)
            }
        roomOffJp.find(normalized)?.let {
            val room = it.groupValues[1].trim()
            if (room.isNotEmpty()) return roomLightsMatch(room, on = false)
        }
        roomOnJp.find(normalized)?.let {
            val room = it.groupValues[1].trim()
            if (room.isNotEmpty()) return roomLightsMatch(room, on = true)
        }

        if (onPatterns.any { it.containsMatchIn(normalized) }) {
            return FastPathMatch(
                toolName = "execute_command",
                arguments = mapOf("device_type" to "light", "action" to "turn_on"),
                spokenConfirmation = "Lights on."
            )
        }
        if (offPatterns.any { it.containsMatchIn(normalized) }) {
            return FastPathMatch(
                toolName = "execute_command",
                arguments = mapOf("device_type" to "light", "action" to "turn_off"),
                spokenConfirmation = "Lights off."
            )
        }
        if (normalized.contains("light") || normalized.contains("brightness")) {
            brightnessSetEn.find(normalized)?.let { m ->
                return brightnessMatch(m.groupValues[1].toInt().coerceIn(0, 100))
            }
        }
        brightnessSetJa.find(normalized)?.let { m ->
            return brightnessMatch(m.groupValues[1].toInt().coerceIn(0, 100))
        }
        if (dimPatterns.any { it.containsMatchIn(normalized) }) return brightnessMatch(30)
        if (brighterPatterns.any { it.containsMatchIn(normalized) }) return brightnessMatch(80)
        return null
    }

    private fun roomLightsMatch(room: String, on: Boolean) = FastPathMatch(
        toolName = "execute_command",
        arguments = mapOf(
            "device_type" to "light",
            "action" to if (on) "turn_on" else "turn_off",
            "room" to room
        ),
        spokenConfirmation = if (on) "$room lights on." else "$room lights off."
    )

    private fun brightnessMatch(pct: Int) = FastPathMatch(
        toolName = "execute_command",
        arguments = mapOf(
            "device_type" to "light",
            "action" to "set_brightness",
            "parameters" to mapOf("brightness" to pct)
        ),
        spokenConfirmation = "Brightness $pct%."
    )
}

/** "pause music", "play", "next track", "前の曲" — targets all media_player devices. */
object MediaControlMatcher : FastPathMatcher {
    private data class Pattern(val regex: Regex, val haAction: String, val spoken: String)

    private val patterns = listOf(
        Pattern(Regex("""(?:pause|stop)\s+(?:music|media|song|audio|track)"""), "media_pause", "Paused."),
        Pattern(Regex("""(?:play|resume)\s+(?:music|media|song|audio)"""), "media_play", "Playing."),
        Pattern(Regex("""(?:skip|next)\s*(?:track|song)?"""), "media_next_track", "Next."),
        Pattern(Regex("""(?:previous|prev|last)\s*(?:track|song)"""), "media_previous_track", "Previous."),
        Pattern(Regex("""(?:音楽|曲|再生)\s*(?:を)?\s*(?:止めて|ストップ|一時停止)"""), "media_pause", "一時停止しました。"),
        Pattern(Regex("""(?:音楽|曲)\s*(?:を)?\s*(?:再生|かけて|流して)"""), "media_play", "再生します。"),
        Pattern(Regex("""次\s*の\s*曲"""), "media_next_track", "次の曲を流します。"),
        Pattern(Regex("""前\s*の\s*曲"""), "media_previous_track", "前の曲を流します。")
    )

    override fun tryMatch(normalized: String): FastPathMatch? {
        for (p in patterns) {
            if (p.regex.containsMatchIn(normalized)) {
                return FastPathMatch(
                    toolName = "execute_command",
                    arguments = mapOf("device_type" to "media_player", "action" to p.haAction),
                    spokenConfirmation = p.spoken
                )
            }
        }
        return null
    }
}

/** "run X routine", "X ルーチンを実行" */
object RunRoutineMatcher : FastPathMatcher {
    private val englishRegex = Regex("""(?:run|execute|trigger)\s+(?:the\s+)?(.+?)\s+routine\s*[!?.]*\s*$""")
    private val japaneseRegex = Regex("""(.+?)\s*ルーチン\s*(?:を)?\s*(?:実行|起動|やって)""")

    override fun tryMatch(normalized: String): FastPathMatch? {
        englishRegex.matchEntire(normalized.trim())?.let {
            val name = it.groupValues[1].trim()
            if (name.isEmpty()) return null
            return FastPathMatch(
                toolName = "run_routine",
                arguments = mapOf("name" to name),
                spokenConfirmation = "Running $name."
            )
        }
        japaneseRegex.matchEntire(normalized.trim())?.let {
            val name = it.groupValues[1].trim()
            if (name.isEmpty()) return null
            return FastPathMatch(
                toolName = "run_routine",
                arguments = mapOf("name" to name),
                spokenConfirmation = "${name}を実行します。"
            )
        }
        return null
    }
}

/** "open X" / "launch X" / "Xを開いて" — forwards to launch_app. */
object LaunchAppMatcher : FastPathMatcher {
    private val englishRegex = Regex("""(?:open|launch|start|run)\s+(?:the\s+)?(.+?)(?:\s+app)?\s*[!?.]*\s*$""")
    private val japaneseRegex = Regex("""(.+?)\s*(?:を)?\s*(?:開いて|立ち上げて|起動して)""")

    override fun tryMatch(normalized: String): FastPathMatch? {
        englishRegex.matchEntire(normalized.trim())?.let {
            val name = it.groupValues[1].trim()
            if (name.isEmpty()) return null
            if (name.startsWith("light") || name.startsWith("timer")) return null
            return FastPathMatch(
                toolName = "launch_app",
                arguments = mapOf("app_name" to name),
                spokenConfirmation = "Opening $name."
            )
        }
        japaneseRegex.matchEntire(normalized.trim())?.let {
            val name = it.groupValues[1].trim().removeSuffix("アプリ").trim()
            if (name.isEmpty()) return null
            return FastPathMatch(
                toolName = "launch_app",
                arguments = mapOf("app_name" to name),
                spokenConfirmation = "${name}を開きます。"
            )
        }
        return null
    }
}

/** "find my phone/tablet/device", "デバイスを探して" */
object FindDeviceMatcher : FastPathMatcher {
    private val patterns = listOf(
        Regex("""(?:find|where('?s|\s+is))\s+(?:my\s+)?(?:phone|tablet|device|speaker)"""),
        Regex("""(?:デバイス|タブレット|スピーカー|端末)\s*(?:を)?\s*(?:探して|どこ)""")
    )

    override fun tryMatch(normalized: String): FastPathMatch? {
        for (p in patterns) {
            if (p.containsMatchIn(normalized)) {
                return FastPathMatch(
                    toolName = "find_device",
                    arguments = emptyMap(),
                    spokenConfirmation = "Ringing now."
                )
            }
        }
        return null
    }
}

/** "I'm home", "ただいま" → arrive_home composite */
object ArriveHomeMatcher : FastPathMatcher {
    private val patterns = listOf(
        Regex("""^\s*(?:i'?m|im)\s+home\s*[!?.]*\s*$"""),
        Regex("""^\s*(?:i'?m|im)\s+back\s*[!?.]*\s*$"""),
        Regex("""^\s*ただいま\s*[!?.]*\s*$""")
    )

    override fun tryMatch(normalized: String): FastPathMatch? {
        for (p in patterns) {
            if (p.containsMatchIn(normalized)) {
                return FastPathMatch(
                    toolName = "arrive_home",
                    arguments = emptyMap(),
                    spokenConfirmation = "Welcome home."
                )
            }
        }
        return null
    }
}

/** "leaving", "I'm leaving", "going out", "行ってきます" → leave_home composite */
object LeaveHomeMatcher : FastPathMatcher {
    private val patterns = listOf(
        Regex("""^\s*(?:i'?m|im)\s+(?:leaving|going\s+out)\s*[!?.]*\s*$"""),
        Regex("""^\s*leaving\s+(?:home|now)?\s*[!?.]*\s*$"""),
        Regex("""^\s*行ってきます\s*[!?.]*\s*$"""),
        Regex("""^\s*出かけます\s*[!?.]*\s*$""")
    )

    override fun tryMatch(normalized: String): FastPathMatch? {
        for (p in patterns) {
            if (p.containsMatchIn(normalized)) {
                return FastPathMatch(
                    toolName = "leave_home",
                    arguments = emptyMap(),
                    spokenConfirmation = "See you later."
                )
            }
        }
        return null
    }
}

/**
 * "goodnight", "good night", "おやすみ" — composite shutdown that turns off
 * lights, pauses media, and cancels timers in one shot.
 *
 * Matches the standalone form so it doesn't fire on every casual sign-off.
 * Distinct from GreetingMatcher's "good evening" / "good night" pleasantry —
 * GoodnightMatcher is in the matcher list before that one to win precedence.
 */
object GoodnightMatcher : FastPathMatcher {
    private val englishRegex = Regex(
        """^\s*good[\s-]?night(?:\s+(?:please|now|everyone))?\s*[!?.]*\s*$"""
    )
    private val sleepRegex = Regex(
        """^\s*(?:i'?m|im)\s+(?:going\s+to\s+(?:sleep|bed)|off\s+to\s+bed)\s*[!?.]*\s*$"""
    )
    private val timeToSleepRegex = Regex(
        """^\s*time\s+(?:to|for)\s+(?:sleep|bed)\s*[!?.]*\s*$"""
    )
    private val japaneseRegex = Regex("""^\s*おやすみ(?:なさい)?\s*[!?.]*\s*$""")
    private val japaneseSleepRegex = Regex("""^\s*寝ます\s*[!?.]*\s*$""")

    override fun tryMatch(normalized: String): FastPathMatch? {
        val matched = englishRegex.containsMatchIn(normalized) ||
            sleepRegex.containsMatchIn(normalized) ||
            timeToSleepRegex.containsMatchIn(normalized) ||
            japaneseRegex.containsMatchIn(normalized) ||
            japaneseSleepRegex.containsMatchIn(normalized)
        if (!matched) return null
        return FastPathMatch(
            toolName = "goodnight",
            arguments = emptyMap(),
            spokenConfirmation = "Goodnight."
        )
    }
}

/** "morning briefing" → composite weather + news + calendar */
object MorningBriefingMatcher : FastPathMatcher {
    private val patterns = listOf(
        Regex("""(?:good\s+)?morning\s+(?:briefing|summary|update)"""),
        Regex("""(?:朝|今朝)\s*(?:の)?\s*(?:ブリーフィング|まとめ|サマリー)""")
    )

    override fun tryMatch(normalized: String): FastPathMatch? {
        for (p in patterns) {
            if (p.containsMatchIn(normalized)) {
                return FastPathMatch(toolName = "morning_briefing", arguments = emptyMap())
            }
        }
        return null
    }
}

/** "evening briefing" / "wind down" → composite notifications + calendar + timers */
object EveningBriefingMatcher : FastPathMatcher {
    private val patterns = listOf(
        Regex("""(?:good\s+)?evening\s+(?:briefing|summary|update)"""),
        Regex("""wind\s+down(?:\s+briefing)?"""),
        Regex("""(?:夜|今夜|寝る前)\s*(?:の)?\s*(?:ブリーフィング|まとめ|サマリー)""")
    )

    override fun tryMatch(normalized: String): FastPathMatch? {
        for (p in patterns) {
            if (p.containsMatchIn(normalized)) {
                return FastPathMatch(toolName = "evening_briefing", arguments = emptyMap())
            }
        }
        return null
    }
}

/** "what's the weather", "today's weather", "今日の天気" */
object WeatherMatcher : FastPathMatcher {
    private val patterns = listOf(
        Regex("""(?:what'?s\s+)?the\s+weather"""),
        Regex("""weather\s+(?:today|now|outside)?"""),
        Regex("""(?:今日|きょう)\s*(?:の)?\s*(?:天気|てんき)"""),
        Regex("""(?:天気|てんき)\s*(?:は|を教えて)""")
    )

    override fun tryMatch(normalized: String): FastPathMatch? {
        for (p in patterns) {
            if (p.containsMatchIn(normalized)) {
                return FastPathMatch(toolName = "get_weather", arguments = emptyMap())
            }
        }
        return null
    }
}

/** "news", "tell me the news", "ニュース" → get_news */
object NewsMatcher : FastPathMatcher {
    private val patterns = listOf(
        Regex("""^\s*(?:the\s+)?news\s*[!?.]*\s*$"""),
        Regex("""(?:what'?s|tell\s+me)\s+(?:the\s+)?(?:today'?s\s+)?news"""),
        Regex("""(?:news|headlines)\s+(?:briefing|today|now)"""),
        Regex("""(?:今日|きょう)?\s*(?:の)?\s*ニュース""")
    )

    override fun tryMatch(normalized: String): FastPathMatch? {
        for (p in patterns) {
            if (p.containsMatchIn(normalized)) {
                return FastPathMatch(toolName = "get_news", arguments = emptyMap())
            }
        }
        return null
    }
}

/**
 * "list devices", "what devices do I have", "デバイス一覧" →
 * get_devices_by_type with type=light as a sane default. Diagnostic
 * fast-path so the user can quickly check what's connected.
 */
object ListDevicesMatcher : FastPathMatcher {
    private val patterns = listOf(
        Regex("""^\s*list\s+(?:my\s+)?devices?\s*[!?.]*\s*$"""),
        Regex("""what\s+(?:devices?|smart\s+home)\s+do\s+i\s+have"""),
        Regex("""^\s*デバイス(?:一覧|の一覧|を見せて)?\s*[!?.]*\s*$""")
    )

    override fun tryMatch(normalized: String): FastPathMatch? {
        for (p in patterns) {
            if (p.containsMatchIn(normalized)) {
                return FastPathMatch(
                    toolName = "get_devices_by_type",
                    arguments = mapOf("type" to "light")
                )
            }
        }
        return null
    }
}

/**
 * "what do you remember", "list memories", "覚えていること" → list_memory
 * with no prefix. Returns whatever's in the memory store so the LLM (or
 * the user, via the speak-back) can audit it.
 */
object ListMemoryMatcher : FastPathMatcher {
    private val patterns = listOf(
        Regex("""what\s+do\s+you\s+remember"""),
        Regex("""list\s+(?:my\s+)?memor(?:y|ies)"""),
        Regex("""(?:覚えて|記憶して)(?:いる|る)?\s*(?:こと|もの)""")
    )

    override fun tryMatch(normalized: String): FastPathMatch? {
        for (p in patterns) {
            if (p.containsMatchIn(normalized)) {
                return FastPathMatch(toolName = "list_memory", arguments = emptyMap())
            }
        }
        return null
    }
}

/** "what's today's date" / "今日は何日" */
object DatetimeMatcher : FastPathMatcher {
    private val patterns = listOf(
        Regex("""what'?s\s+(?:the\s+)?(?:today'?s?\s+)?date"""),
        Regex("""what\s+day\s+is\s+(?:it|today)"""),
        Regex("""今日\s*(?:は)?\s*何日""")
    )

    override fun tryMatch(normalized: String): FastPathMatch? {
        for (p in patterns) {
            if (p.containsMatchIn(normalized)) {
                return FastPathMatch(toolName = "get_datetime", arguments = emptyMap())
            }
        }
        return null
    }
}

/** Social pleasantries — speak-only canned replies. */
object GreetingMatcher : FastPathMatcher {
    private data class Rule(val regex: Regex, val reply: String)

    private val rules = listOf(
        Rule(Regex("""^\s*(?:thanks|thank\s+you|thx|ty)\s*[!?.]*\s*$"""), "You're welcome."),
        Rule(Regex("""^\s*(?:hi|hello|hey)(?:\s+there)?\s*[!?.]*\s*$"""), "Hi. What can I help with?"),
        Rule(Regex("""^\s*good\s+morning\s*[!?.]*\s*$"""), "Good morning."),
        Rule(Regex("""^\s*good\s+(?:evening|night)\s*[!?.]*\s*$"""), "Good evening."),
        Rule(Regex("""^\s*sorry\s*[!?.]*\s*$"""), "No problem."),
        Rule(Regex("""ありがとう"""), "どういたしまして。"),
        Rule(Regex("""こんにちは"""), "こんにちは。何かお手伝いできますか。"),
        Rule(Regex("""おはよう"""), "おはようございます。"),
        Rule(Regex("""こんばんは"""), "こんばんは。"),
        Rule(Regex("""ごめん(?:なさい)?"""), "気にしないでください。")
    )

    override fun tryMatch(normalized: String): FastPathMatch? {
        for (rule in rules) {
            if (rule.regex.containsMatchIn(normalized)) {
                return FastPathMatch(toolName = null, spokenConfirmation = rule.reply)
            }
        }
        return null
    }
}

/** "help", "what can you do", "できることを教えて" — speak-only capability summary. */
object HelpMatcher : FastPathMatcher {
    private val englishPatterns = listOf(
        Regex("""^\s*help\s*[!?.]*\s*$"""),
        Regex("""what\s+can\s+you\s+do"""),
        Regex("""what\s+can\s+i\s+(?:ask|say)""")
    )
    private val japanesePatterns = listOf(
        Regex("""ヘルプ"""),
        Regex("""使い方"""),
        Regex("""(?:できる|出来る)こと.*(?:教えて|おしえて|知りたい)""")
    )

    private const val EN_HELP = "Try things like: set a timer, volume up, turn the lights on, " +
        "what time is it, or ask me a question. Say 'help' anytime."
    private const val JA_HELP = "例えば、タイマーをセット、音量を上げて、電気をつけて、今何時、" +
        "などと話しかけてみてください。"

    override fun tryMatch(normalized: String): FastPathMatch? {
        if (englishPatterns.any { it.containsMatchIn(normalized) }) {
            return FastPathMatch(toolName = null, spokenConfirmation = EN_HELP)
        }
        if (japanesePatterns.any { it.containsMatchIn(normalized) }) {
            return FastPathMatch(toolName = null, spokenConfirmation = JA_HELP)
        }
        return null
    }
}
