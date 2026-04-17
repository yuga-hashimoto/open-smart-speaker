package com.opensmarthome.speaker.voice.fastpath

import java.time.LocalDateTime

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

/**
 * Alarm at a wall-clock time: "set an alarm for 7am", "wake me up at 6:30",
 * "7時にアラーム". Must sit AFTER [TimerMatcher] in the router order — if
 * the utterance is a duration-based "timer", TimerMatcher wins first and
 * this matcher never runs.
 *
 * No dedicated `set_alarm` tool exists on-device, so the matcher computes
 * the remaining seconds until the requested wall-clock time (rolls to
 * tomorrow if the target has already passed today) and dispatches
 * `set_timer` with that payload.
 *
 * `nowProvider` is injected for deterministic tests; production uses
 * [LocalDateTime.now].
 */
class AlarmMatcherImpl(
    private val nowProvider: () -> LocalDateTime = { LocalDateTime.now() }
) : FastPathMatcher {
    private val englishSetAlarm = Regex(
        """set\s+(?:an?\s+|the\s+)?alarm\s+(?:for\s+)?(\d{1,2})(?::(\d{2}))?\s*(am|pm)?"""
    )
    private val englishWakeMeUp = Regex(
        """wake\s+me\s+up\s+(?:at\s+)?(\d{1,2})(?::(\d{2}))?\s*(am|pm)?"""
    )
    private val japaneseRegex = Regex(
        """(\d{1,2})時(?:(\d{1,2})分?)?\s*(?:に)?\s*(?:アラーム|目覚まし|起こして)"""
    )

    override fun tryMatch(normalized: String): FastPathMatch? {
        // English variants
        val enMatch = englishSetAlarm.find(normalized) ?: englishWakeMeUp.find(normalized)
        if (enMatch != null) {
            val rawHour = enMatch.groupValues[1].toIntOrNull() ?: return null
            val minute = enMatch.groupValues[2].toIntOrNull() ?: 0
            val amPm = enMatch.groupValues[3].takeIf { it.isNotEmpty() }
            val hour = AlarmTimeCalculator.normalizeHour(rawHour, amPm) ?: return null
            if (minute !in 0..59) return null
            val seconds = AlarmTimeCalculator.secondsUntil(nowProvider(), hour, minute)
            return FastPathMatch(
                toolName = "set_timer",
                arguments = mapOf("seconds" to seconds.toDouble()),
                spokenConfirmation = englishConfirmation(hour, minute, amPm)
            )
        }
        // Japanese
        japaneseRegex.find(normalized)?.let { m ->
            val rawHour = m.groupValues[1].toIntOrNull() ?: return null
            val minute = m.groupValues[2].toIntOrNull() ?: 0
            if (rawHour !in 0..23 || minute !in 0..59) return null
            val seconds = AlarmTimeCalculator.secondsUntil(nowProvider(), rawHour, minute)
            val label = if (minute == 0) "${rawHour}時" else "${rawHour}時${minute}分"
            return FastPathMatch(
                toolName = "set_timer",
                arguments = mapOf("seconds" to seconds.toDouble()),
                spokenConfirmation = "${label}のアラームを設定しました。"
            )
        }
        return null
    }

    private fun englishConfirmation(hour: Int, minute: Int, originalAmPm: String?): String {
        // Echo back the user's chosen format when possible.
        val suffix = originalAmPm?.lowercase()
        val displayHour: Int
        val displaySuffix: String
        if (suffix != null) {
            displayHour = when {
                hour == 0 -> 12
                hour > 12 -> hour - 12
                else -> hour
            }
            displaySuffix = if (hour < 12) "am" else "pm"
        } else {
            displayHour = hour
            displaySuffix = ""
        }
        val minStr = if (minute == 0) "" else ":%02d".format(minute)
        val trailing = if (displaySuffix.isEmpty()) "" else " $displaySuffix"
        return "Alarm set for $displayHour$minStr$trailing."
    }
}

/**
 * Singleton default [AlarmMatcherImpl] with real system time, registered
 * in [DefaultFastPathRouter.DEFAULT_MATCHERS]. Tests should construct a
 * fresh [AlarmMatcherImpl] with a fixed `nowProvider`.
 */
object AlarmMatcher : FastPathMatcher {
    private val delegate = AlarmMatcherImpl()
    override fun tryMatch(normalized: String): FastPathMatch? = delegate.tryMatch(normalized)
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

/**
 * "fan on" / "fan off" / "ファンをつけて" / "扇風機を消して" → execute_command
 * device_type=fan. Keeps itself narrow (anchored to the fan noun) to
 * avoid swallowing "turn on" for other devices.
 */
object FanMatcher : FastPathMatcher {
    private val onPatterns = listOf(
        Regex("""(?:turn\s+)?(?:the\s+)?fan\s+on"""),
        Regex("""(?:扇風機|ファン)\s*(?:を)?\s*(?:つけて|オン|回して)""")
    )
    private val offPatterns = listOf(
        Regex("""(?:turn\s+)?(?:the\s+)?fan\s+off"""),
        Regex("""(?:扇風機|ファン)\s*(?:を)?\s*(?:消して|オフ|止めて)""")
    )

    override fun tryMatch(normalized: String): FastPathMatch? {
        if (onPatterns.any { it.containsMatchIn(normalized) }) {
            return FastPathMatch(
                toolName = "execute_command",
                arguments = mapOf("device_type" to "fan", "action" to "turn_on"),
                spokenConfirmation = "Fan on."
            )
        }
        if (offPatterns.any { it.containsMatchIn(normalized) }) {
            return FastPathMatch(
                toolName = "execute_command",
                arguments = mapOf("device_type" to "fan", "action" to "turn_off"),
                spokenConfirmation = "Fan off."
            )
        }
        return null
    }
}

/**
 * "TV on" / "TV off" / "turn on the TV" / "テレビをつけて" / "テレビを消して"
 * → execute_command device_type=media_player. Anchored to the TV /
 * television / テレビ noun.
 *
 * MediaControlMatcher already handles play/pause for a generic media
 * player; this one targets the physical TV on-off.
 */
object TvMatcher : FastPathMatcher {
    private val onPatterns = listOf(
        Regex("""(?:turn\s+)?(?:the\s+)?(?:tv|television)\s+on"""),
        Regex("""(?:tv|television)\s+on"""),
        Regex("""テレビ\s*(?:を)?\s*(?:つけて|オン)""")
    )
    private val offPatterns = listOf(
        Regex("""(?:turn\s+)?(?:the\s+)?(?:tv|television)\s+off"""),
        Regex("""(?:tv|television)\s+off"""),
        Regex("""テレビ\s*(?:を)?\s*(?:消して|オフ|切って)""")
    )

    override fun tryMatch(normalized: String): FastPathMatch? {
        if (onPatterns.any { it.containsMatchIn(normalized) }) {
            return FastPathMatch(
                toolName = "execute_command",
                arguments = mapOf("device_type" to "media_player", "action" to "turn_on"),
                spokenConfirmation = "TV on."
            )
        }
        if (offPatterns.any { it.containsMatchIn(normalized) }) {
            return FastPathMatch(
                toolName = "execute_command",
                arguments = mapOf("device_type" to "media_player", "action" to "turn_off"),
                spokenConfirmation = "TV off."
            )
        }
        return null
    }
}

/**
 * Covers / blinds / curtains / garage: "open the blinds", "close the
 * garage", "カーテンを開けて", "ブラインドを閉めて" → execute_command
 * device_type=cover, action=open_cover/close_cover. Conservative —
 * anchored to explicit cover nouns so it doesn't swallow other opens.
 */
object CoverMatcher : FastPathMatcher {
    private val openPatterns = listOf(
        Regex("""open\s+(?:the\s+)?(?:blind|blinds|curtain|curtains|shade|shades|shutter|shutters|garage|garage\s+door|gate)"""),
        Regex("""(?:カーテン|ブラインド|シャッター|シャッタ|シェード|ガレージ|雨戸)\s*(?:を)?\s*(?:開けて|開いて|あけて)""")
    )
    private val closePatterns = listOf(
        Regex("""close\s+(?:the\s+)?(?:blind|blinds|curtain|curtains|shade|shades|shutter|shutters|garage|garage\s+door|gate)"""),
        Regex("""(?:カーテン|ブラインド|シャッター|シャッタ|シェード|ガレージ|雨戸)\s*(?:を)?\s*(?:閉めて|閉じて|しめて)""")
    )

    override fun tryMatch(normalized: String): FastPathMatch? {
        if (openPatterns.any { it.containsMatchIn(normalized) }) {
            return FastPathMatch(
                toolName = "execute_command",
                arguments = mapOf("device_type" to "cover", "action" to "open_cover"),
                spokenConfirmation = "Opening."
            )
        }
        if (closePatterns.any { it.containsMatchIn(normalized) }) {
            return FastPathMatch(
                toolName = "execute_command",
                arguments = mapOf("device_type" to "cover", "action" to "close_cover"),
                spokenConfirmation = "Closing."
            )
        }
        return null
    }
}

/**
 * "lock the door" / "unlock the door" / "ドアをロック" / "玄関を解錠" →
 * execute_command device_type=lock. Conservative pattern anchored to
 * an explicit lock noun (door / front door / ドア / 玄関).
 */
object LockMatcher : FastPathMatcher {
    private val lockPatterns = listOf(
        Regex("""\block\s+(?:the\s+)?(?:door|front\s+door|back\s+door|gate)"""),
        Regex("""(?:ドア|玄関|扉|ゲート)\s*(?:を)?\s*(?:ロック|施錠)""")
    )
    private val unlockPatterns = listOf(
        Regex("""\bunlock\s+(?:the\s+)?(?:door|front\s+door|back\s+door|gate)"""),
        Regex("""(?:ドア|玄関|扉|ゲート)\s*(?:を)?\s*(?:アンロック|解錠)""")
    )

    override fun tryMatch(normalized: String): FastPathMatch? {
        // Check unlock before lock because "unlock" contains "lock".
        if (unlockPatterns.any { it.containsMatchIn(normalized) }) {
            return FastPathMatch(
                toolName = "execute_command",
                arguments = mapOf("device_type" to "lock", "action" to "unlock"),
                spokenConfirmation = "Unlocked."
            )
        }
        if (lockPatterns.any { it.containsMatchIn(normalized) }) {
            return FastPathMatch(
                toolName = "execute_command",
                arguments = mapOf("device_type" to "lock", "action" to "lock"),
                spokenConfirmation = "Locked."
            )
        }
        return null
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

/**
 * Deep-links into system Settings screens: "open wifi settings", "bluetooth
 * settings", "brightness settings", "Wi-Fiの設定", "明るさ", "音量", "設定を開いて" …
 * → `open_settings_page` with a `page` slug matching
 * [com.opensmarthome.speaker.tool.system.OpenSettingsToolExecutor] enum.
 *
 * Must sit BEFORE [LaunchAppMatcher] — otherwise utterances like "open wifi
 * settings" fall through to launch_app and try to start a non-existent
 * "wifi settings" app.
 */
object SettingsMatcher : FastPathMatcher {
    private data class Rule(val regex: Regex, val slug: String, val spoken: String)

    private val rules = listOf(
        // English — order matters: most specific first so "open settings" doesn't
        // swallow "open wifi settings".
        Rule(Regex("""\bopen\s+wi-?fi\s+settings\b"""), "wifi", "Opening Wi-Fi settings."),
        Rule(Regex("""\bwi-?fi\s+settings\b"""), "wifi", "Opening Wi-Fi settings."),
        Rule(Regex("""\b(?:open\s+)?bluetooth\s+settings\b"""), "bluetooth", "Opening Bluetooth settings."),
        Rule(Regex("""\bbrightness\s+settings\b"""), "brightness", "Opening display settings."),
        Rule(Regex("""\bdisplay\s+settings\b"""), "display", "Opening display settings."),
        Rule(Regex("""\bsound\s+settings\b"""), "sound", "Opening sound settings."),
        Rule(Regex("""\bvolume\s+settings\b"""), "volume", "Opening sound settings."),
        Rule(Regex("""\baccessibility\s+settings\b"""), "accessibility", "Opening accessibility settings."),
        Rule(Regex("""\bnotification\s+settings\b"""), "notifications", "Opening notification settings."),
        Rule(Regex("""\bapp\s+(?:settings|list)\b"""), "apps", "Opening app settings."),
        Rule(Regex("""\bbattery\s+(?:saver\s+)?settings\b"""), "battery", "Opening battery settings."),
        // "settings" / "open settings" / "system settings" — catch-all last.
        Rule(Regex("""^\s*(?:open\s+|system\s+)?settings\s*[!?.]*\s*$"""), "home", "Opening settings."),
        // Japanese
        Rule(Regex("""wi-?fi\s*の?\s*設定"""), "wifi", "Wi-Fi設定を開きます。"),
        Rule(Regex("""(?:ブルートゥース|ブルートゥ|ブルー\s*トゥース)\s*の?\s*設定"""), "bluetooth", "Bluetooth設定を開きます。"),
        Rule(Regex("""明るさ(?:\s*の?\s*設定)?"""), "brightness", "明るさの設定を開きます。"),
        Rule(Regex("""音量(?:\s*の?\s*設定)?"""), "volume", "音量の設定を開きます。"),
        Rule(Regex("""アクセシビリティ(?:\s*の?\s*設定)?"""), "accessibility", "アクセシビリティ設定を開きます。"),
        Rule(Regex("""通知\s*の?\s*設定"""), "notifications", "通知設定を開きます。"),
        Rule(Regex("""アプリ\s*の?\s*設定"""), "apps", "アプリ設定を開きます。"),
        Rule(Regex("""バッテリー\s*の?\s*設定"""), "battery", "バッテリー設定を開きます。"),
        Rule(
            Regex("""^\s*(?:システム\s*)?設定(?:を)?\s*(?:開いて|ひらいて)?\s*[!?.]*\s*$"""),
            "home",
            "設定を開きました。"
        )
    )

    override fun tryMatch(normalized: String): FastPathMatch? {
        for (rule in rules) {
            if (rule.regex.containsMatchIn(normalized)) {
                return FastPathMatch(
                    toolName = "open_settings_page",
                    arguments = mapOf("page" to rule.slug),
                    spokenConfirmation = rule.spoken
                )
            }
        }
        return null
    }
}

/**
 * Opens an explicit http/https URL, or "open <domain>.<tld>" utterances, via
 * the `open_url` tool. Must sit BEFORE [LaunchAppMatcher] so that utterances
 * like "open example.com" resolve to a URL open instead of a launch_app
 * search for an app called "example.com".
 *
 * Two forms:
 * 1. Literal URL in the utterance — e.g. "open https://example.com/path".
 * 2. Bare domain — e.g. "open example.com" / "open the site example.com" —
 *    gets an `https://` prefix before dispatch.
 *
 * Only `http` / `https` are considered here; any other scheme (file://,
 * intent://, content://, javascript:) is never emitted because the regex
 * won't capture it. The executor defends the same allow-list regardless.
 */
object OpenUrlMatcher : FastPathMatcher {
    private val explicitUrlRegex = Regex("""(https?://\S+)""")
    private val openDomainRegex = Regex(
        """(?:^|\s)open\s+(?:the\s+)?(?:website\s+|page\s+|site\s+)?([a-z0-9][a-z0-9.-]*\.[a-z]{2,}(?:/\S*)?)"""
    )

    override fun tryMatch(normalized: String): FastPathMatch? {
        explicitUrlRegex.find(normalized)?.let { m ->
            val url = m.groupValues[1].trimEnd('.', ',', '!', '?', ')', ']')
            val host = hostOf(url)
            return FastPathMatch(
                toolName = "open_url",
                arguments = mapOf("url" to url),
                spokenConfirmation = host?.let { "Opening $it." }
            )
        }
        openDomainRegex.find(normalized)?.let { m ->
            val captured = m.groupValues[1].trimEnd('.', ',', '!', '?', ')', ']')
            val host = captured.substringBefore('/')
            val url = "https://$captured"
            return FastPathMatch(
                toolName = "open_url",
                arguments = mapOf("url" to url),
                spokenConfirmation = "Opening $host."
            )
        }
        return null
    }

    private fun hostOf(url: String): String? {
        val afterScheme = url.substringAfter("://", missingDelimiterValue = "")
        if (afterScheme.isEmpty()) return null
        val hostPort = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        return hostPort.ifEmpty { null }
    }
}

/** "open X" / "launch X" / "Xを開いて" — forwards to launch_app. */
object LaunchAppMatcher : FastPathMatcher {
    private val englishRegex = Regex("""(?:open|launch|start|run)\s+(?:the\s+)?(.+?)(?:\s+app)?\s*[!?.]*\s*$""")
    private val japaneseRegex = Regex("""(.+?)\s*(?:を)?\s*(?:開いて|立ち上げて|起動して)""")

    /**
     * Names that should never be interpreted as apps even if they match the
     * "open/launch/start X" pattern. These are smart-home controllables
     * (doors, blinds, locks, thermostats…) that the LLM can route to
     * execute_command, plus timer/light keywords that their own matchers
     * already own.
     */
    private val smartHomeReservedPrefixes = listOf(
        "light", "timer",
        "door", "garage", "gate",
        "blind", "curtain", "shade", "window",
        "lock", "unlock",
        "thermostat", "ac", "air conditioner",
        "fan", "tv", "television",
        "speaker", "music", "playlist",
        "oven", "microwave",
        // settings deep-links are owned by SettingsMatcher; guard so an
        // unmatched "open wifi settings" doesn't accidentally launch_app.
        "settings", "wifi", "wi-fi", "bluetooth", "brightness"
    )
    private val japaneseReservedSubstrings = listOf(
        "ドア", "扉", "玄関",
        "カーテン", "ブラインド", "シャッター", "窓",
        "鍵", "ロック",
        "エアコン", "扇風機",
        "テレビ", "音楽"
    )

    override fun tryMatch(normalized: String): FastPathMatch? {
        englishRegex.matchEntire(normalized.trim())?.let {
            val name = it.groupValues[1].trim()
            if (name.isEmpty()) return null
            if (smartHomeReservedPrefixes.any { keyword -> name.startsWith(keyword) }) return null
            return FastPathMatch(
                toolName = "launch_app",
                arguments = mapOf("app_name" to name),
                spokenConfirmation = "Opening $name."
            )
        }
        japaneseRegex.matchEntire(normalized.trim())?.let {
            val name = it.groupValues[1].trim().removeSuffix("アプリ").trim()
            if (name.isEmpty()) return null
            if (japaneseReservedSubstrings.any { keyword -> name.contains(keyword) }) return null
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
                return FastPathMatch(
                    toolName = "morning_briefing",
                    arguments = emptyMap(),
                    spokenConfirmation = "Here's your morning briefing."
                )
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
                return FastPathMatch(
                    toolName = "evening_briefing",
                    arguments = emptyMap(),
                    spokenConfirmation = "Here's your evening briefing."
                )
            }
        }
        return null
    }
}

/** "what's the weather", "today's weather", "今日の天気" */
/**
 * Forecast queries — "what's the weather tomorrow", "this week's forecast",
 * "明日の天気", "今週の天気" → get_forecast.
 *
 * Must precede WeatherMatcher because both contain the substring "weather"
 * / "天気" but the forward-looking flavor needs the multi-day forecast.
 */
object ForecastMatcher : FastPathMatcher {
    private val englishPatterns = listOf(
        Regex("""(?:what'?s\s+)?(?:the\s+)?weather\s+(?:tomorrow|this\s+(?:week|weekend)|for\s+(?:the\s+)?(?:week|weekend))"""),
        Regex("""(?:tomorrow'?s|this\s+week'?s|weekend'?s)\s+(?:weather|forecast)"""),
        Regex("""forecast(?:\s+for\s+(?:tomorrow|this\s+week|the\s+week|the\s+weekend))?"""),
        Regex("""(?:will\s+it|is\s+it\s+going\s+to)\s+rain\s+(?:tomorrow|this\s+(?:week|weekend))""")
    )
    private val japanesePatterns = listOf(
        Regex("""明日\s*(?:の)?\s*(?:天気|てんき)"""),
        Regex("""今週\s*(?:の)?\s*(?:天気|てんき)"""),
        Regex("""週末\s*(?:の)?\s*(?:天気|てんき)"""),
        Regex("""(?:天気)?予報""")
    )

    override fun tryMatch(normalized: String): FastPathMatch? {
        if (englishPatterns.any { it.containsMatchIn(normalized) } ||
            japanesePatterns.any { it.containsMatchIn(normalized) }
        ) {
            return FastPathMatch(toolName = "get_forecast", arguments = emptyMap())
        }
        return null
    }
}

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
 * "what's on my calendar today", "do I have any meetings", "今日の予定" → get_calendar_events.
 * Defaults to days_ahead=1 (today). Multi-day briefings should use the LLM
 * (or the morning_briefing composite) to phrase the response.
 */
object CalendarMatcher : FastPathMatcher {
    private val englishPatterns = listOf(
        Regex("""what'?s\s+on\s+my\s+calendar(?:\s+today)?"""),
        Regex("""(?:my|the)\s+calendar\s+(?:today|for\s+today)"""),
        Regex("""(?:do\s+i|i)\s+have\s+(?:any\s+)?(?:meetings?|events?|appointments?)\s+(?:today|this\s+(?:morning|afternoon))?"""),
        Regex("""any\s+(?:meetings?|events?|appointments?)\s+today"""),
        Regex("""what'?s\s+(?:on\s+)?(?:today'?s|my)\s+schedule""")
    )
    private val japanesePatterns = listOf(
        Regex("""今日\s*(?:の)?\s*(?:予定|スケジュール|ミーティング|会議)"""),
        Regex("""(?:予定|スケジュール)\s*(?:を)?\s*(?:教えて|確認)"""),
        Regex("""今日\s*(?:は)?\s*(?:何か|なにか)\s*(?:予定|ある)""")
    )

    override fun tryMatch(normalized: String): FastPathMatch? {
        if (englishPatterns.any { it.containsMatchIn(normalized) } ||
            japanesePatterns.any { it.containsMatchIn(normalized) }
        ) {
            return FastPathMatch(
                toolName = "get_calendar_events",
                arguments = mapOf("days_ahead" to 1.0)
            )
        }
        return null
    }
}

/**
 * "clear notifications", "dismiss all notifications", "通知を消して" → clear_notifications.
 * Must precede ListNotificationsMatcher because "clear" is a stronger verb.
 */
object ClearNotificationsMatcher : FastPathMatcher {
    private val patterns = listOf(
        Regex("""(?:clear|dismiss|delete|remove)\s+(?:all\s+)?(?:my\s+)?notifications?"""),
        Regex("""通知\s*(?:を)?\s*(?:全部|全て)?\s*(?:消して|クリア|削除)""")
    )

    override fun tryMatch(normalized: String): FastPathMatch? {
        for (p in patterns) {
            if (p.containsMatchIn(normalized)) {
                return FastPathMatch(
                    toolName = "clear_notifications",
                    arguments = emptyMap(),
                    spokenConfirmation = "Notifications cleared."
                )
            }
        }
        return null
    }
}

/**
 * "what notifications do I have", "show notifications", "any notifications",
 * "通知一覧" / "通知ある" → list_notifications.
 */
object ListNotificationsMatcher : FastPathMatcher {
    private val englishPatterns = listOf(
        Regex("""(?:list|show|see|read)\s+(?:my\s+|all\s+)?notifications?"""),
        Regex("""what\s+notifications?\s+(?:do\s+i\s+have|are\s+there)"""),
        Regex("""any\s+(?:new\s+)?notifications?"""),
        Regex("""(?:do\s+i\s+have|got)\s+(?:any\s+)?notifications?""")
    )
    private val japanesePatterns = listOf(
        Regex("""通知\s*(?:の)?\s*(?:一覧|リスト)"""),
        Regex("""通知\s*(?:は|を)?\s*(?:ある|教えて|見せて)""")
    )

    override fun tryMatch(normalized: String): FastPathMatch? {
        if (englishPatterns.any { it.containsMatchIn(normalized) } ||
            japanesePatterns.any { it.containsMatchIn(normalized) }
        ) {
            return FastPathMatch(toolName = "list_notifications", arguments = emptyMap())
        }
        return null
    }
}

/**
 * "where am I", "what's my location", "ここはどこ" → get_location.
 *
 * Distinct from FindDeviceMatcher which rings the device. This one
 * answers a location query.
 */
object LocationMatcher : FastPathMatcher {
    private val englishPatterns = listOf(
        Regex("""where\s+am\s+i\s*[!?.]*\s*$"""),
        Regex("""what'?s\s+my\s+location"""),
        Regex("""what\s+city\s+am\s+i\s+in"""),
        Regex("""(?:get|tell\s+me)\s+(?:my\s+)?(?:current\s+)?location""")
    )
    private val japanesePatterns = listOf(
        Regex("""ここ\s*(?:は)?\s*どこ"""),
        Regex("""現在(?:地|位置)\s*(?:を|は)?\s*(?:教えて|どこ)?"""),
        Regex("""(?:私|僕)\s*(?:は)?\s*どこ\s*(?:に)?\s*(?:いる|いますか)?""")
    )

    override fun tryMatch(normalized: String): FastPathMatch? {
        if (englishPatterns.any { it.containsMatchIn(normalized) } ||
            japanesePatterns.any { it.containsMatchIn(normalized) }
        ) {
            return FastPathMatch(toolName = "get_location", arguments = emptyMap())
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

/**
 * "list timers", "what timers do I have", "show my timers", "タイマー一覧" → get_timers.
 *
 * Must precede CancelAllTimersMatcher? No — this matcher is registered
 * AFTER CancelAllTimersMatcher in DEFAULT_MATCHERS, so "cancel all timers"
 * still wins. We only fire on list/show/what queries.
 */
object ListTimersMatcher : FastPathMatcher {
    private val patterns = listOf(
        Regex("""(?:list|show|see)\s+(?:my\s+|all\s+)?timers?"""),
        Regex("""what\s+timers?\s+(?:do\s+i\s+have|are\s+(?:set|running|going))"""),
        Regex("""(?:any|how\s+many)\s+timers?\s+(?:running|set|going|left)"""),
        Regex("""タイマー\s*(?:の)?\s*(?:一覧|リスト|残り|何個)"""),
        Regex("""残ってる\s*タイマー""")
    )

    override fun tryMatch(normalized: String): FastPathMatch? {
        for (p in patterns) {
            if (p.containsMatchIn(normalized)) {
                return FastPathMatch(toolName = "get_timers", arguments = emptyMap())
            }
        }
        return null
    }
}

/**
 * "system status", "device health", "how much storage", "診断", "ストレージ残り" →
 * get_device_health. The tool itself produces the spoken answer, so this
 * matcher sets no spokenConfirmation.
 *
 * Registered before [DatetimeMatcher] / [GreetingMatcher] / [HelpMatcher] so
 * pleasantries like "how is it going" don't swallow "how is the device
 * running". The patterns here require device/system/storage/memory context.
 */
/** "lock the screen", "screen off", "スクリーンロック" → `lock_screen` tool (P15.10). */
/**
 * "broadcast X to the kitchen (group)" / "キッチングループに X ってアナウンス" →
 * `broadcast_tts` tool with a `group` argument. Must be registered
 * **before** [BroadcastTtsMatcher] so group-scoped utterances win;
 * unscoped "broadcast X to all speakers" then falls through to
 * [BroadcastTtsMatcher].
 *
 * Capture layout: group 1 = message, group 2 = group name. Group names
 * are arbitrary user-defined strings (matched case-insensitively in the
 * repository), so the regex is deliberately permissive — it only
 * requires a non-blank token after "to the".
 */
object BroadcastGroupMatcher : FastPathMatcher {
    // English — "broadcast X to the kitchen", "announce X to upstairs speakers",
    // "tell the bedroom speakers X". Group name captured as a bare word or a
    // multi-word run before an optional "speakers"/"group" suffix.
    private val englishMessageFirst = Regex(
        "^\\s*(?:broadcast|announce)\\s+(.+?)\\s+to\\s+(?:the\\s+)?(.+?)(?:\\s+(?:speakers?|group))?\\.?$"
    )
    private val englishGroupFirst = Regex(
        "^\\s*tell\\s+(?:the\\s+)?(.+?)\\s+(?:speakers?|group)\\s+(.+?)\\.?$"
    )
    private val japaneseGroupFirst = Regex(
        "^(.+?)(?:グループ|ルーム)?(?:に|へ)(.+?)(?:って|を)(?:アナウンス|放送|伝えて|流して)\\s*$"
    )
    private val japaneseMessageFirst = Regex(
        "^(.+?)って(.+?)(?:グループ|ルーム)(?:に|へ)(?:アナウンス|放送|伝えて|流して)\\s*$"
    )

    /**
     * Reserved group tokens that [BroadcastTtsMatcher] already handles as
     * "everyone" — keeping them here would steal broadcasts that the
     * unscoped matcher is supposed to own.
     */
    private val allEveryoneTokens = setOf(
        "all", "all speakers", "everyone", "every", "every speaker",
        "全員", "みんな", "全スピーカー", "全部屋"
    )

    override fun tryMatch(normalized: String): FastPathMatch? {
        englishMessageFirst.find(normalized)?.let { m ->
            val msg = m.groupValues[1].trim().trim('"')
            val group = m.groupValues[2].trim().trim('"')
            if (msg.isNotBlank() && isValidGroup(group)) {
                return buildMatch(msg, group, language = "en")
            }
        }
        englishGroupFirst.find(normalized)?.let { m ->
            val group = m.groupValues[1].trim().trim('"')
            val msg = m.groupValues[2].trim().trim('"')
            if (msg.isNotBlank() && isValidGroup(group)) {
                return buildMatch(msg, group, language = "en")
            }
        }
        japaneseMessageFirst.find(normalized)?.let { m ->
            val msg = m.groupValues[1].trim().trim('「', '」', '"')
            val group = m.groupValues[2].trim().trim('「', '」', '"')
            if (msg.isNotBlank() && isValidGroup(group)) {
                return buildMatch(msg, group, language = "ja")
            }
        }
        japaneseGroupFirst.find(normalized)?.let { m ->
            val group = m.groupValues[1].trim().trim('「', '」', '"')
            val msg = m.groupValues[2].trim().trim('「', '」', '"')
            if (msg.isNotBlank() && isValidGroup(group)) {
                return buildMatch(msg, group, language = "ja")
            }
        }
        return null
    }

    private fun isValidGroup(token: String): Boolean =
        token.isNotBlank() && token.lowercase() !in allEveryoneTokens

    private fun buildMatch(text: String, group: String, language: String) = FastPathMatch(
        toolName = "broadcast_tts",
        arguments = mapOf(
            "text" to text,
            "language" to language,
            "group" to group
        )
    )
}

/**
 * "list nearby speakers" / "who's on the network" / "近くのスピーカー" → `list_peers`.
 * Narrow token set so unrelated utterances pass through.
 */
object ListPeersMatcher : FastPathMatcher {
    private val englishPatterns = listOf(
        Regex("""\blist\s+(?:nearby|paired|connected)\s+speakers?\b"""),
        Regex("""\b(?:what|which)\s+speakers?\s+(?:are|do)\s+(?:nearby|connected|i\s+have)\b"""),
        Regex("""\bwho(?:'s|\s+is)\s+on\s+the\s+network\b"""),
        Regex("""\bnearby\s+speakers?\b""")
    )
    private val japanesePatterns = listOf(
        Regex("""近(?:く|所)の\s*スピーカー"""),
        Regex("""(?:周り|周辺|ペアリング済み)(?:の)?\s*スピーカー"""),
        Regex("""スピーカー(?:を)?(?:一覧|リスト)""")
    )

    override fun tryMatch(normalized: String): FastPathMatch? {
        if (englishPatterns.any { it.containsMatchIn(normalized) } ||
            japanesePatterns.any { it.containsMatchIn(normalized) }
        ) {
            return FastPathMatch(toolName = "list_peers", arguments = emptyMap())
        }
        return null
    }
}

/**
 * "broadcast X to all speakers" / "全スピーカーに [text] ってアナウンスして" →
 * `broadcast_tts` tool. Captures the message so we route to the tool with the
 * right argument; needs a non-trivial message or a bare "broadcast" falls
 * through to the LLM.
 */
object BroadcastTtsMatcher : FastPathMatcher {
    // Order matters: more specific first. Each regex captures group 1 = message.
    private val englishPatterns = listOf(
        Regex("^\\s*(?:broadcast|announce)\\s+(.+?)\\s+to\\s+(?:all|everyone|every)(?:\\s+speakers?)?\\.?$"),
        Regex("^\\s*tell\\s+(?:all|every)\\s+speakers?\\s+(.+?)\\.?$")
    )
    private val japanesePatterns = listOf(
        Regex("(?:全スピーカー|全員|みんな|全部屋)(?:に)?(?:アナウンス|放送)(?:して)?[:：]\\s*(.+?)[\\s。]*$"),
        Regex("^(.+?)(?:って|を)(?:全員|みんな|全スピーカー)(?:に|へ)(?:伝えて|アナウンスして|放送して)$")
    )

    override fun tryMatch(normalized: String): FastPathMatch? {
        for (regex in englishPatterns) {
            regex.find(normalized)?.let { m ->
                val msg = m.groupValues[1].trim().trim('"')
                if (msg.isNotBlank()) {
                    return FastPathMatch(
                        toolName = "broadcast_tts",
                        arguments = mapOf("text" to msg, "language" to "en")
                    )
                }
            }
        }
        for (regex in japanesePatterns) {
            regex.find(normalized)?.let { m ->
                val msg = m.groupValues[1].trim().trim('「', '」', '"')
                if (msg.isNotBlank()) {
                    return FastPathMatch(
                        toolName = "broadcast_tts",
                        arguments = mapOf("text" to msg, "language" to "ja")
                    )
                }
            }
        }
        return null
    }
}

object LockScreenMatcher : FastPathMatcher {
    private val englishPatterns = listOf(
        Regex("""\block\s+(?:the\s+)?(?:screen|tablet|device|phone)\b"""),
        Regex("""\bscreen\s+off\b"""),
        Regex("""\bgo\s+to\s+lock\s+screen\b""")
    )
    private val japanesePatterns = listOf(
        Regex("""(?:画面|スクリーン)(?:を)?ロック"""),
        Regex("""ロック(?:して|してください)"""),
        Regex("""画面(?:を)?消して""")
    )

    override fun tryMatch(normalized: String): FastPathMatch? {
        if (englishPatterns.any { it.containsMatchIn(normalized) } ||
            japanesePatterns.any { it.containsMatchIn(normalized) }
        ) {
            return FastPathMatch(toolName = "lock_screen", arguments = emptyMap())
        }
        return null
    }
}

object DeviceHealthMatcher : FastPathMatcher {
    private val englishPatterns = listOf(
        Regex("""\bsystem\s+status\b"""),
        Regex("""\bdevice\s+health\b"""),
        Regex("""\bhow\s+is\s+the\s+(?:device|system)\s+(?:doing|running)\b"""),
        Regex("""\bstorage\s+(?:space|available|free)\b"""),
        Regex("""\bmemory\s+(?:available|free)\b"""),
        Regex("""\bhow\s+much\s+storage\b""")
    )
    private val japanesePatterns = listOf(
        Regex("""システム(?:状態|状況|ステータス)"""),
        Regex("""端末状態"""),
        Regex("""診断"""),
        Regex("""ストレージ.*(?:残り|空き|容量)"""),
        Regex("""メモリ.*(?:残り|空き|容量)""")
    )

    override fun tryMatch(normalized: String): FastPathMatch? {
        if (englishPatterns.any { it.containsMatchIn(normalized) } ||
            japanesePatterns.any { it.containsMatchIn(normalized) }
        ) {
            return FastPathMatch(toolName = "get_device_health", arguments = emptyMap())
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
        // Thanks variants — covers "thanks", "thank you", "thank you so much",
        // "thanks a lot", "many thanks", "thx", "ty", "appreciate it/that".
        Rule(
            Regex(
                """^\s*(?:""" +
                    """(?:many\s+)?thanks?(?:\s+(?:a\s+lot|so\s+much|very\s+much|again))?""" +
                    """|thank\s+you(?:\s+(?:so\s+much|very\s+much|kindly))?""" +
                    """|thx""" +
                    """|ty""" +
                    """|appreciate\s+(?:it|that|you)""" +
                    """)\s*[!?.]*\s*$"""
            ),
            "You're welcome."
        ),
        Rule(Regex("""^\s*(?:hi|hello|hey)(?:\s+there)?\s*[!?.]*\s*$"""), "Hi. What can I help with?"),
        Rule(Regex("""^\s*good\s+morning\s*[!?.]*\s*$"""), "Good morning."),
        Rule(Regex("""^\s*good\s+(?:evening|night)\s*[!?.]*\s*$"""), "Good evening."),
        Rule(Regex("""^\s*sorry\s*[!?.]*\s*$"""), "No problem."),
        Rule(Regex("""ありがとう"""), "どういたしまして。"),
        Rule(Regex("""感謝"""), "どういたしまして。"),
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

/**
 * Session handoff (P17.5) — "move this to the kitchen speaker",
 * "send to bedroom", "キッチンにハンドオフ" → `handoff_session` tool with
 * `target=<captured peer>`. The matcher passes whatever the user said;
 * the broadcaster performs prefix/substring matching against discovered
 * serviceNames, so both "kitchen" and "speaker-kitchen" resolve.
 *
 * Deliberately narrow: the English side requires the "this / the
 * conversation / the session / it" object to avoid swallowing every
 * "move to X" / "send to X" utterance (those can mean a lot of things).
 *
 * Must sit BEFORE generic LaunchAppMatcher — "move" and "send" aren't
 * handled by launch_app, but it's cheap insurance.
 */
object HandoffMatcher : FastPathMatcher {
    // English: "move this to the kitchen speaker", "move the conversation
    // to bedroom", "send (this|the session) to <peer>", "hand this off to <peer>",
    // "handoff to <peer>".
    private val englishRegex = Regex(
        """(?:move|send|transfer|hand(?:\s+this)?\s+off)\s+""" +
            """(?:this|the\s+(?:conversation|session|chat|call)|it)?\s*""" +
            """(?:to\s+)(?:the\s+)?""" +
            """(.+?)(?:\s+speaker)?\s*[!?.]*\s*$"""
    )
    // Also accept "handoff to <peer>" without the leading verb variants.
    private val englishHandoff = Regex(
        """handoff\s+to\s+(?:the\s+)?(.+?)(?:\s+speaker)?\s*[!?.]*\s*$"""
    )
    // Japanese: "キッチンにハンドオフ", "寝室に移して", "リビングに送って".
    private val japaneseRegex = Regex(
        """^(.+?)\s*(?:に|へ)\s*(?:ハンドオフ|移して|移動|送って|送信|転送)\s*[!?.]*\s*$"""
    )

    override fun tryMatch(normalized: String): FastPathMatch? {
        val trimmed = normalized.trim()
        englishHandoff.matchEntire(trimmed)?.let { m ->
            return buildMatch(m.groupValues[1])
        }
        englishRegex.matchEntire(trimmed)?.let { m ->
            return buildMatch(m.groupValues[1])
        }
        japaneseRegex.matchEntire(trimmed)?.let { m ->
            return buildMatch(m.groupValues[1])
        }
        return null
    }

    private fun buildMatch(rawTarget: String): FastPathMatch? {
        val target = rawTarget.trim().removePrefix("the ").trim()
        if (target.isEmpty()) return null
        return FastPathMatch(
            toolName = "handoff_session",
            arguments = mapOf("target" to target),
            spokenConfirmation = "Moving to $target."
        )
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

    private const val EN_HELP = "Try: set a timer, turn the lights on, what's the weather, " +
        "tell me the news, run the morning routine, or ask me anything. " +
        "I can also remember things, search your documents, and run skills."
    private const val JA_HELP = "例えば、タイマーをセット、電気をつけて、今日の天気、ニュース、" +
        "朝のルーチンを実行、などと話しかけてください。" +
        "メモやドキュメント検索、スキルの実行もできます。"

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

/**
 * "cancel timers on all speakers", "全スピーカーのタイマーをキャンセル".
 *
 * Scoped variant of [CancelAllTimersMatcher]. Must precede it in the
 * router order so utterances that explicitly name every speaker go
 * through the multi-room tool path instead of being swallowed as a
 * local cancel. Patterns require the explicit "on all/every speakers"
 * or "全スピーカー" qualifier so plain "cancel all timers" still falls
 * through to [CancelAllTimersMatcher].
 */
object BroadcastCancelTimerMatcher : FastPathMatcher {
    private val englishRegex = Regex(
        """(?:cancel|stop|clear)\s+(?:all\s+)?timers?\s+on\s+(?:all|every)\s+speakers?"""
    )
    private val japaneseRegex = Regex(
        """全スピーカー(?:の)?タイマー(?:を)?(?:キャンセル|取り消し|止めて|やめて)"""
    )

    override fun tryMatch(normalized: String): FastPathMatch? {
        if (englishRegex.containsMatchIn(normalized) || japaneseRegex.containsMatchIn(normalized)) {
            return FastPathMatch(
                toolName = "broadcast_cancel_timer",
                arguments = emptyMap()
            )
        }
        return null
    }
}

/**
 * "set a 5 minute timer on all speakers", "全スピーカーで5分タイマー".
 *
 * Must sit immediately after [TimerMatcher] in the router order — this
 * matcher's regexes demand the explicit "on all speakers" / "全スピーカー"
 * qualifier, so plain "set timer 5 minutes" still falls through to
 * [TimerMatcher] which routes to the local `set_timer`.
 */
object BroadcastTimerMatcher : FastPathMatcher {
    private val englishRegex = Regex(
        """set\s+(?:a\s+|the\s+)?(\d+)\s+(seconds?|minutes?|hours?|min|sec|hr)\s+timer\s+on\s+(?:all|every)\s+speakers?"""
    )
    private val japaneseRegex = Regex(
        """全スピーカー(?:で|に)(\d+)\s*(秒|分|時間)(?:の)?タイマー"""
    )

    override fun tryMatch(normalized: String): FastPathMatch? {
        englishRegex.find(normalized)?.let { m ->
            val n = m.groupValues[1].toInt()
            val unit = m.groupValues[2].lowercase()
            val seconds = toSeconds(n, unit) ?: return null
            return FastPathMatch(
                toolName = "broadcast_timer",
                arguments = mapOf("seconds" to seconds.toDouble()),
                spokenConfirmation = "Setting a $n ${unit.trimEnd('s')}${if (n != 1) "s" else ""} timer on every speaker."
            )
        }
        japaneseRegex.find(normalized)?.let { m ->
            val n = m.groupValues[1].toInt()
            val unit = m.groupValues[2]
            val seconds = when (unit) {
                "秒" -> n
                "分" -> n * 60
                "時間" -> n * 3600
                else -> return null
            }
            return FastPathMatch(
                toolName = "broadcast_timer",
                arguments = mapOf("seconds" to seconds.toDouble()),
                spokenConfirmation = "全スピーカーで${n}${unit}のタイマーを設定します。"
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

/**
 * "flip a coin" / "コインを投げて" — routes to the `flip_coin` tool from
 * [com.opensmarthome.speaker.tool.info.RandomToolExecutor]. The fast
 * path skips the LLM round-trip because the request is trivially
 * unambiguous; the executor picks the side, the matcher only hands off
 * a confirmation the TTS can speak while the result lands.
 */
object FlipCoinMatcher : FastPathMatcher {
    private val englishRegex = Regex(
        """^\s*(?:flip|toss)\s+(?:a\s+)?coin\.?\s*$"""
    )
    private val japaneseRegex = Regex(
        """^\s*コイン(?:を)?(?:投げ|振っ)て\s*(?:ください|下さい)?\s*[。.]?\s*$"""
    )

    override fun tryMatch(normalized: String): FastPathMatch? {
        if (englishRegex.containsMatchIn(normalized)) {
            return FastPathMatch(
                toolName = "flip_coin",
                arguments = emptyMap(),
                spokenConfirmation = "Flipping…"
            )
        }
        if (japaneseRegex.containsMatchIn(normalized)) {
            return FastPathMatch(
                toolName = "flip_coin",
                arguments = emptyMap(),
                spokenConfirmation = "コインを投げます。"
            )
        }
        return null
    }
}

/**
 * "roll a d20", "roll 3d6", "roll d6", "サイコロを振って" —
 * fast-paths straight to the `roll_dice` tool on
 * [com.opensmarthome.speaker.tool.info.RandomToolExecutor].
 *
 * Defaults mirror the tool's own defaults: sides = 6, count = 1. The
 * regex keeps the utterance strict (optional trailing period, no extra
 * clauses) so compound sentences like "roll a d20 and tell me the
 * weather" fall through to the LLM. Japanese variant is single-die
 * 6-sided only — dice-notation in JA speech is uncommon, so we lean on
 * the LLM for anything richer than "サイコロを振って".
 */
object RollDiceMatcher : FastPathMatcher {
    // "roll [a|an] d20", "roll 3 d6" — count optional, sides required.
    private val englishRegex = Regex(
        """^\s*roll\s+(?:(\d+)\s*)?(?:a\s+|an\s+)?d(\d+)\s*\.?\s*$"""
    )
    // "roll 3d6" shorthand — count and sides glued together.
    private val englishShorthand = Regex(
        """^\s*roll\s+(\d+)d(\d+)\s*\.?\s*$"""
    )
    // Default single 6-sided die for casual Japanese.
    private val japaneseRegex = Regex(
        """^\s*(?:サイコロ|さいころ)(?:を)?振って\s*(?:ください|下さい)?\s*[。.]?\s*$"""
    )

    override fun tryMatch(normalized: String): FastPathMatch? {
        englishShorthand.matchEntire(normalized)?.let { m ->
            val count = m.groupValues[1].toIntOrNull() ?: return null
            val sides = m.groupValues[2].toIntOrNull() ?: return null
            return buildMatch(sides = sides, count = count)
        }
        englishRegex.matchEntire(normalized)?.let { m ->
            val count = m.groupValues[1].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 1
            val sides = m.groupValues[2].toIntOrNull() ?: return null
            return buildMatch(sides = sides, count = count)
        }
        japaneseRegex.matchEntire(normalized)?.let {
            return FastPathMatch(
                toolName = "roll_dice",
                arguments = mapOf("sides" to 6.0, "count" to 1.0),
                spokenConfirmation = "サイコロを振ります。"
            )
        }
        return null
    }

    private fun buildMatch(sides: Int, count: Int): FastPathMatch = FastPathMatch(
        toolName = "roll_dice",
        arguments = mapOf("sides" to sides.toDouble(), "count" to count.toDouble()),
        spokenConfirmation = "Rolling $count d$sides…"
    )
}

/**
 * "pick one of pizza, sushi, ramen", "choose between coffee and tea",
 * "ピザ、ラーメン、寿司から選んで".
 *
 * Extracts the candidate list, splits on commas (JA "、"/","), and
 * forwards as a comma-joined string to the `pick_random` tool on
 * [com.opensmarthome.speaker.tool.info.RandomToolExecutor].
 *
 * Sits near the tail of the router — patterns are narrow (explicit
 * "pick/choose/select … of/from/between" verbs or the JA
 * "…から選んで" / "…の中から…選んで" suffix) so plain "pick up the phone"
 * utterances pass through to later matchers or the LLM path. Requires
 * at least two options — a single candidate is meaningless for a
 * random pick, so we let it fall through rather than silently
 * returning the only input.
 */
object PickRandomMatcher : FastPathMatcher {
    private val englishRegex = Regex(
        """^\s*(?:pick|choose|select)\s+(?:one|something)?\s*(?:of|from|between)\s+(.+?)\.?\s*$"""
    )
    private val japaneseRegex = Regex(
        // Try "の中から" before "から" — the longer suffix is a strict prefix
        // of the shorter match position, and regex alternation is leftmost
        // so we need the specific form to win when both are viable.
        """^\s*(.+?)(?:の中から|から)\s*(?:一つ|ひとつ|どれか)?(?:選んで|ランダムで)\s*(?:ください|下さい)?\s*[。.]?\s*$"""
    )

    override fun tryMatch(normalized: String): FastPathMatch? {
        englishRegex.find(normalized)?.let { m ->
            val options = splitEnglish(m.groupValues[1])
            if (options.size < 2) return null
            return FastPathMatch(
                toolName = "pick_random",
                arguments = mapOf("options" to options.joinToString(","))
            )
        }
        japaneseRegex.find(normalized)?.let { m ->
            val options = splitJapanese(m.groupValues[1])
            if (options.size < 2) return null
            return FastPathMatch(
                toolName = "pick_random",
                arguments = mapOf("options" to options.joinToString(","))
            )
        }
        return null
    }

    private fun splitEnglish(raw: String): List<String> {
        // Accept "a, b, c" / "a, b, and c" / "a and b" / "a or b".
        val commaParts = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val expanded = commaParts.flatMap { part ->
            part.split(Regex("""\s+(?:and|or)\s+"""))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }
        // Strip leading "and "/"or " that survive the Oxford-comma form
        // ("apple, banana, and cherry" → the tail piece starts with "and ").
        return expanded
            .map { it.removePrefix("and ").removePrefix("or ").trim() }
            .filter { it.isNotEmpty() }
    }

    private fun splitJapanese(raw: String): List<String> {
        // Accept "A、B、C" / "A,B,C". Do NOT split on "か" — it's a
        // legitimate mid-word mora (みかん, すいか, からあげ…), and getting
        // it wrong silently corrupts options rather than falling through
        // to the LLM path.
        return raw.split(Regex("""[、,]"""))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }
}
