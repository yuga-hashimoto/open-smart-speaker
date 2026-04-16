package com.opensmarthome.speaker.voice.fastpath

/**
 * Routes simple, high-frequency utterances directly to tool calls
 * without round-tripping through the LLM. Target latency <200ms from
 * final STT result to tool execution.
 *
 * This is the "Alexa feel" path — the LLM is still there for the long
 * tail of complex requests, but canonical smart-speaker commands fire
 * instantly.
 *
 * Patterns are language-aware; default matchers cover English + Japanese.
 */
interface FastPathRouter {
    fun match(utterance: String): FastPathMatch?
}

data class FastPathMatch(
    /** Tool to invoke, or null for speak-only responses (e.g. "help"). */
    val toolName: String?,
    val arguments: Map<String, Any?> = emptyMap(),
    /** Short confirmation phrase the TTS can speak while/after the tool runs. */
    val spokenConfirmation: String? = null
)

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
            TimeQueryMatcher,
            VolumeMatcher,
            LightsMatcher,
            MediaControlMatcher,
            LaunchAppMatcher,
            DatetimeMatcher,
            GreetingMatcher,
            HelpMatcher
        )
    }
}

interface FastPathMatcher {
    fun tryMatch(normalized: String): FastPathMatch?
}

/** "set timer 5 minutes", "5 分タイマー" */
object TimerMatcher : FastPathMatcher {
    // English: "(set )?timer (for )?<n> (min|minute|second|hour)"
    private val englishRegex = Regex(
        """(?:set\s+)?timer\s*(?:for\s+)?(\d+)\s*(seconds?|minutes?|hours?|min|sec|hr)"""
    )
    // Japanese: "5分タイマー" / "10秒タイマー"
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
                return FastPathMatch(
                    toolName = "get_datetime",
                    arguments = emptyMap()
                )
            }
        }
        return null
    }
}

/** "volume up", "louder", "音量上げて" */
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
            return FastPathMatch(
                toolName = "set_volume",
                arguments = mapOf("level" to level.toDouble())
            )
        }
        if (mutePatterns.any { it.containsMatchIn(normalized) }) {
            return FastPathMatch(
                toolName = "set_volume",
                arguments = mapOf("level" to 0.0),
                spokenConfirmation = "Muted."
            )
        }
        if (unmutePatterns.any { it.containsMatchIn(normalized) }) {
            return FastPathMatch(
                toolName = "set_volume",
                arguments = mapOf("level" to 50.0),
                spokenConfirmation = "Unmuted."
            )
        }
        if (upPatterns.any { it.containsMatchIn(normalized) }) {
            return FastPathMatch(
                toolName = "set_volume",
                arguments = mapOf("level" to 70.0), // sensible default bump
                spokenConfirmation = "Volume up."
            )
        }
        if (downPatterns.any { it.containsMatchIn(normalized) }) {
            return FastPathMatch(
                toolName = "set_volume",
                arguments = mapOf("level" to 30.0),
                spokenConfirmation = "Volume down."
            )
        }
        return null
    }
}

/** "lights on/off", "電気つけて/消して" */
object LightsMatcher : FastPathMatcher {
    // Note: without room context this is vague, but covers "all lights" style.
    private val onPatterns = listOf(
        Regex("""(?:turn\s+)?(?:the\s+)?lights?\s+on"""),
        Regex("""(?:電気|ライト|明かり)\s*(?:を\s*)?(?:つけて|点けて|オン)""")
    )
    private val offPatterns = listOf(
        Regex("""(?:turn\s+)?(?:the\s+)?lights?\s+off"""),
        Regex("""(?:電気|ライト|明かり)\s*(?:を\s*)?(?:消して|けして|オフ)""")
    )

    override fun tryMatch(normalized: String): FastPathMatch? {
        if (onPatterns.any { it.containsMatchIn(normalized) }) {
            return FastPathMatch(
                toolName = "execute_command",
                arguments = mapOf(
                    "device_type" to "light",
                    "action" to "turn_on"
                ),
                spokenConfirmation = "Lights on."
            )
        }
        if (offPatterns.any { it.containsMatchIn(normalized) }) {
            return FastPathMatch(
                toolName = "execute_command",
                arguments = mapOf(
                    "device_type" to "light",
                    "action" to "turn_off"
                ),
                spokenConfirmation = "Lights off."
            )
        }
        return null
    }
}

/**
 * "pause music", "play", "next track", "前の曲", etc.
 *
 * Targets all media_player devices (no favorite-device concept yet); users
 * with multiple speakers should disambiguate by name via the LLM path.
 */
object MediaControlMatcher : FastPathMatcher {
    private data class Pattern(val regex: Regex, val haAction: String, val spoken: String)

    private val patterns = listOf(
        Pattern(Regex("""(?:pause|stop)\s+(?:music|media|song|audio|track)"""), "media_pause", "Paused."),
        Pattern(Regex("""(?:play|resume)\s+(?:music|media|song|audio)"""), "media_play", "Playing."),
        Pattern(Regex("""(?:skip|next)\s*(?:track|song)?"""), "media_next_track", "Next."),
        Pattern(Regex("""(?:previous|prev|last)\s*(?:track|song)"""), "media_previous_track", "Previous."),
        // Japanese
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
                    arguments = mapOf(
                        "device_type" to "media_player",
                        "action" to p.haAction
                    ),
                    spokenConfirmation = p.spoken
                )
            }
        }
        return null
    }
}

/**
 * "open X" / "launch X" / "Xを開いて" — forwards directly to launch_app.
 * The AppLauncher handles fuzzy name matching server-side.
 */
object LaunchAppMatcher : FastPathMatcher {
    private val englishRegex = Regex("""(?:open|launch|start|run)\s+(?:the\s+)?(.+?)(?:\s+app)?\s*[!?.]*\s*$""")
    private val japaneseRegex = Regex("""(.+?)\s*(?:を)?\s*(?:開いて|立ち上げて|起動して)""")

    override fun tryMatch(normalized: String): FastPathMatch? {
        englishRegex.matchEntire(normalized.trim())?.let {
            val name = it.groupValues[1].trim()
            if (name.isEmpty()) return null
            // Avoid stealing from other matchers (e.g. "open the lights")
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

/**
 * Social pleasantries. Speak a canned reply so the user gets instant feedback
 * and the LLM isn't bothered for trivial turn-taking.
 */
object GreetingMatcher : FastPathMatcher {
    private data class Rule(val regex: Regex, val reply: String)

    private val rules = listOf(
        // English
        Rule(Regex("""^\s*(?:thanks|thank\s+you|thx|ty)\s*[!?.]*\s*$"""), "You're welcome."),
        Rule(Regex("""^\s*(?:hi|hello|hey)(?:\s+there)?\s*[!?.]*\s*$"""), "Hi. What can I help with?"),
        Rule(Regex("""^\s*good\s+morning\s*[!?.]*\s*$"""), "Good morning."),
        Rule(Regex("""^\s*good\s+(?:evening|night)\s*[!?.]*\s*$"""), "Good evening."),
        Rule(Regex("""^\s*sorry\s*[!?.]*\s*$"""), "No problem."),
        // Japanese
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

/**
 * "help", "what can you do", "できることを教えて"
 *
 * Returns a speak-only match (toolName=null) whose spokenConfirmation is a
 * short, friendly capability summary. Lets users discover features without
 * paying for an LLM round-trip or reading documentation.
 */
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

/** "what's today's date" */
object DatetimeMatcher : FastPathMatcher {
    private val patterns = listOf(
        Regex("""what'?s\s+(?:the\s+)?(?:today'?s?\s+)?date"""),
        Regex("""what\s+day\s+is\s+(?:it|today)"""),
        Regex("""今日\s*(?:は)?\s*何日""")
    )

    override fun tryMatch(normalized: String): FastPathMatch? {
        for (p in patterns) {
            if (p.containsMatchIn(normalized)) {
                return FastPathMatch(
                    toolName = "get_datetime",
                    arguments = emptyMap()
                )
            }
        }
        return null
    }
}
