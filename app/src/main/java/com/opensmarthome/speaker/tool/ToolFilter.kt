package com.opensmarthome.speaker.tool

/**
 * Filters the full tool list down to ones likely relevant to a given user
 * utterance. With 50+ tools the prompt budget gets tight on a 4k-context
 * on-device LLM; this lets us drop unrelated tools and shrink the prompt
 * by 60-80% on focused requests while still falling back to the full list
 * when no keywords match.
 *
 * The filter is purely a hint — passing the full list still works; this is
 * an optimization for the on-device path. Remote providers with large
 * context can ignore this and use availableTools() directly.
 */
object ToolFilter {

    /**
     * Map of intent → tool name prefixes / exact names that are relevant.
     * Order matters: the first matching intent wins.
     */
    private data class Bucket(val keywords: List<String>, val tools: Set<String>)

    private val buckets = listOf(
        Bucket(
            keywords = listOf("timer", "alarm", "remind", "アラーム", "タイマー"),
            tools = setOf(
                "set_timer", "cancel_timer", "cancel_all_timers", "get_timers",
                "broadcast_timer", "broadcast_cancel_timer",
                "remember", "recall"
            )
        ),
        Bucket(
            keywords = listOf(
                "music", "song", "play", "pause", "track", "album", "spotify",
                "音楽", "曲", "再生"
            ),
            tools = setOf(
                "execute_command", "get_now_playing", "get_devices_by_type", "set_volume"
            )
        ),
        Bucket(
            keywords = listOf("weather", "forecast", "rain", "天気", "予報"),
            tools = setOf("get_weather", "get_forecast", "get_location")
        ),
        Bucket(
            keywords = listOf("light", "lamp", "brightness", "電気", "ライト", "明かり"),
            tools = setOf("execute_command", "get_devices_by_type", "get_devices_by_room", "get_rooms")
        ),
        Bucket(
            keywords = listOf("temperature", "thermostat", "ac", "aircon", "heat", "cool", "エアコン", "温度"),
            tools = setOf("execute_command", "get_devices_by_type", "get_device_state")
        ),
        Bucket(
            keywords = listOf("volume", "mute", "loud", "quiet", "音量", "ミュート"),
            tools = setOf("set_volume", "get_volume")
        ),
        Bucket(
            keywords = listOf("calendar", "schedule", "appointment", "meeting", "予定", "カレンダー"),
            tools = setOf("get_calendar_events", "get_datetime")
        ),
        Bucket(
            keywords = listOf("notification", "notify", "通知"),
            tools = setOf("list_notifications", "clear_notifications")
        ),
        Bucket(
            keywords = listOf("photo", "picture", "image", "camera", "写真", "カメラ"),
            tools = setOf("take_photo", "list_recent_photos")
        ),
        Bucket(
            keywords = listOf("remember", "memory", "recall", "forget", "覚えて", "記憶"),
            tools = setOf("remember", "recall", "search_memory", "semantic_memory_search", "list_memory", "forget")
        ),
        Bucket(
            keywords = listOf("routine", "ルーチン"),
            tools = setOf("run_routine", "list_routines", "delete_routine")
        ),
        Bucket(
            keywords = listOf("skill", "スキル"),
            tools = setOf("get_skill", "list_skills", "install_skill_from_url")
        ),
        Bucket(
            keywords = listOf(
                "search", "google", "look up", "検索",
                // Open-ended information queries — small LLMs often say
                // "I can't search" instead of calling web_search, so surface
                // the tool on these very common triggers.
                "what is", "what's", "whats",
                "who is", "who's", "whos",
                "how to ", "how do ", "explain ", "define ",
                "tell me about",
                // Japanese question / curiosity markers.
                "について", "とは", "詳しく", "知りたい", "調べて", "教えて"
            ),
            tools = setOf("web_search", "fetch_webpage", "get_news")
        ),
        Bucket(
            keywords = listOf(
                "where am i", "location", "current location", "city",
                "ここ", "現在地", "現在位置"
            ),
            tools = setOf("get_location")
        ),
        Bucket(
            keywords = listOf(
                "find my", "where's my", "ring my", "ring the",
                "探して", "見つけて", "鳴らして"
            ),
            tools = setOf("find_device")
        ),
        Bucket(
            keywords = listOf(
                "sms", "text message", "send a message", "送って",
                "メッセージを送", "ショートメール"
            ),
            tools = setOf("send_sms", "search_contacts", "list_contacts")
        ),
        Bucket(
            keywords = listOf(
                "contact", "phone number", "連絡先", "電話番号"
            ),
            tools = setOf("search_contacts", "list_contacts")
        ),
        Bucket(
            keywords = listOf(
                "document", "rag", "ingest", "knowledge base",
                "ドキュメント", "資料"
            ),
            tools = setOf("ingest_document", "retrieve_document", "list_documents", "delete_document")
        ),
        Bucket(
            keywords = listOf(
                "screen recording", "record screen", "スクリーン録画", "画面録画"
            ),
            tools = setOf("start_screen_recording", "stop_screen_recording", "read_screen")
        )
    )

    /**
     * Maximum tools to retain in the fallback path (no bucket matched). When
     * the full list is larger than this we trim to a representative subset
     * to keep the on-device prompt within budget while still giving the LLM
     * a broad safety net.
     */
    const val MAX_FALLBACK_TOOLS = 15

    /**
     * Representative tool names used when no bucket matches. Covers the
     * major categories so the LLM can still pick something sensible.
     */
    private val FALLBACK_REPRESENTATIVES = listOf(
        "web_search", "get_weather", "get_news", "get_datetime",
        "set_timer", "set_volume", "execute_command",
        "get_devices_by_type", "get_rooms",
        "remember", "recall",
        "get_skill", "list_skills",
        "get_location", "get_calendar_events"
    )

    /**
     * Returns a focused subset of [allTools] when [userInput] matches at
     * least one bucket's keywords. On no match, returns the full list when
     * it's small (<= [MAX_FALLBACK_TOOLS]); otherwise trims to a curated
     * representative subset (preserving order within [allTools]).
     */
    fun filterByIntent(allTools: List<ToolSchema>, userInput: String): List<ToolSchema> {
        val text = userInput.lowercase()
        val matchingTools = mutableSetOf<String>()
        for (bucket in buckets) {
            if (bucket.keywords.any { it in text }) {
                matchingTools.addAll(bucket.tools)
            }
        }
        if (matchingTools.isEmpty()) return fallbackSubset(allTools)

        // Always include the agent-control tools so it can introspect / hand off.
        val alwaysOn = setOf(
            "get_datetime", "get_skill", "list_skills",
            "remember", "recall"
        )
        val keep = matchingTools + alwaysOn
        return allTools.filter { it.name in keep }
    }

    private fun fallbackSubset(allTools: List<ToolSchema>): List<ToolSchema> {
        if (allTools.size <= MAX_FALLBACK_TOOLS) return allTools
        // Prefer the representatives in declared order, then backfill with
        // the first remaining tools until we hit the budget.
        val reps = FALLBACK_REPRESENTATIVES.toSet()
        val preferred = allTools.filter { it.name in reps }
        val rest = allTools.filter { it.name !in reps }
        return (preferred + rest).take(MAX_FALLBACK_TOOLS)
    }
}
