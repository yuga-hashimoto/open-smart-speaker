package com.opensmarthome.speaker.voice.pipeline

/**
 * Turns a tool's raw JSON `result.data` into a short, natural-language
 * utterance for TTS in the fast path. Only a handful of info tools
 * (weather, forecast, web_search, news) need bespoke phrasing — every other
 * tool falls back to `"Done."`, matching the previous behaviour.
 *
 * Keep the parsing regex-based and tolerant: the LLM fast-path deliberately
 * avoids bringing in Moshi here so the formatter stays cheap and test-only
 * unit tests don't need Android's `org.json` stub (see `VoskWakeWordDetector`
 * for the same trade-off). If a field is missing we simply omit it from the
 * output rather than failing the turn — speaking "東京は18度、晴れ" is a
 * better experience than speaking "Done." because humidity was absent.
 */
object FastPathResultFormatter {

    private const val FALLBACK = "Done."
    private const val MAX_FORECAST_DAYS = 3
    private const val MAX_NEWS_HEADLINES = 3

    /**
     * @param toolName the canonical tool name (`get_weather`, `get_forecast`,
     *     `web_search`, `get_news`, …).
     * @param data the `ToolResult.data` JSON string produced by the tool
     *     executor. May be empty.
     * @param ttsLanguageTag a BCP-47 tag like `"ja-JP"`, `"en-US"`, `null`, or
     *     `""`. Used only to pick ja vs en phrasing; everything that isn't
     *     `ja*` gets the English template.
     * @return the utterance to hand to [com.opensmarthome.speaker.voice.tts.TextToSpeech.speak].
     */
    fun format(toolName: String, data: String, ttsLanguageTag: String?): String {
        val japanese = isJapanese(ttsLanguageTag)
        return when (toolName) {
            "get_weather" -> formatWeather(data, japanese) ?: FALLBACK
            "get_forecast" -> formatForecast(data, japanese) ?: FALLBACK
            "web_search" -> formatWebSearch(data, japanese) ?: FALLBACK
            "get_news" -> formatNews(data, japanese) ?: FALLBACK
            else -> FALLBACK
        }
    }

    /**
     * Pre-summary used as the grounding facts for
     * [FastPathLlmPolisher.buildPrompt]. Same extraction logic as
     * [format] but emits a short plain-text "Current: …", "Day 1: …",
     * "Abstract: …", "Headlines: …" summary that a 270m-2B on-device
     * model can reliably follow.
     *
     * Returns the empty string when [data] is blank or the tool is not
     * one of the supported info tools; callers should fall back to the
     * raw [data] in that case.
     */
    fun buildPolishSummary(toolName: String, data: String, ttsLanguageTag: String?): String {
        if (data.isBlank()) return ""
        return when (toolName) {
            "get_weather" -> buildWeatherSummary(data)
            "get_forecast" -> buildForecastSummary(data)
            "web_search" -> buildWebSearchSummary(data)
            "get_news" -> buildNewsSummary(data)
            else -> ""
        }
    }

    private fun isJapanese(tag: String?): Boolean {
        if (tag.isNullOrBlank()) return false
        return tag.lowercase().startsWith("ja")
    }

    // --- Pre-summary builders (plain text for the LLM polisher) ---

    private fun buildWeatherSummary(data: String): String {
        val location = stringField(data, "location")
        val tempC = numberField(data, "temperature_c")
        val condition = stringField(data, "condition")
        val humidity = numberField(data, "humidity")
        val windKph = numberField(data, "wind_kph")
        if (location == null && tempC == null && condition == null) return ""
        val parts = mutableListOf<String>()
        if (location != null) parts.add("location $location")
        if (tempC != null) parts.add("${formatInt(tempC)}°C")
        if (!condition.isNullOrBlank()) parts.add(condition)
        if (humidity != null) parts.add("humidity ${formatInt(humidity)}%")
        if (windKph != null) parts.add("wind ${formatInt(windKph)}km/h")
        return "Current: " + parts.joinToString(", ") + "."
    }

    private fun buildForecastSummary(data: String): String {
        val entries = splitObjectArray(data)
        if (entries.isEmpty()) return "No forecast data."
        return entries.take(MAX_FORECAST_DAYS).mapIndexed { idx, entry ->
            val date = stringField(entry, "date")
            val minC = numberField(entry, "min_c")
            val maxC = numberField(entry, "max_c")
            val condition = stringField(entry, "condition")
            val label = date ?: "Day ${idx + 1}"
            val parts = mutableListOf<String>()
            if (minC != null) parts.add("min ${formatInt(minC)}°C")
            if (maxC != null) parts.add("max ${formatInt(maxC)}°C")
            if (!condition.isNullOrBlank()) parts.add(condition)
            "$label: " + parts.joinToString(", ") + "."
        }.joinToString(" ")
    }

    private fun buildWebSearchSummary(data: String): String {
        val abstract = stringField(data, "abstract")
        val related = stringArrayField(data, "related")
        return buildString {
            if (!abstract.isNullOrBlank()) {
                append("Abstract: ")
                append(abstract)
            } else {
                append("Abstract: (empty)")
            }
            if (related.isNotEmpty()) {
                append(" Related: ")
                append(related.take(3).joinToString("; "))
            }
            if (abstract.isNullOrBlank() && related.isEmpty()) {
                append(" No results from the search provider.")
            }
        }
    }

    private fun buildNewsSummary(data: String): String {
        val entries = splitObjectArray(data)
        if (entries.isEmpty()) return "No news headlines available."
        val titles = entries.take(MAX_NEWS_HEADLINES).mapNotNull { stringField(it, "title") }
        if (titles.isEmpty()) return "No news headlines available."
        return "Headlines: " + titles.mapIndexed { i, t -> "${i + 1}) $t" }.joinToString(". ") + "."
    }

    // --- Weather ---

    private fun formatWeather(data: String, japanese: Boolean): String? {
        if (data.isBlank()) return null
        val location = stringField(data, "location") ?: return null
        val tempC = numberField(data, "temperature_c")
        val condition = stringField(data, "condition")
        val humidity = numberField(data, "humidity")
        val windKph = numberField(data, "wind_kph")

        return if (japanese) {
            buildString {
                append(location)
                append("は")
                if (tempC != null) {
                    append(formatInt(tempC))
                    append("度")
                }
                if (!condition.isNullOrBlank()) {
                    if (tempC != null) append("、")
                    append(condition)
                }
                if (humidity != null) {
                    append("、湿度")
                    append(formatInt(humidity))
                    append("パーセント")
                }
                if (windKph != null) {
                    append("、風速")
                    append(formatInt(windKph))
                    append("キロメートル")
                }
                append("です。")
            }
        } else {
            buildString {
                append("In ")
                append(location)
                if (tempC != null) {
                    append(" it's ")
                    append(formatInt(tempC))
                    append(" degrees")
                }
                if (!condition.isNullOrBlank()) {
                    if (tempC != null) append(", ") else append(" ")
                    append(condition.lowercase())
                }
                if (humidity != null) {
                    append(", humidity ")
                    append(formatInt(humidity))
                    append("%")
                }
                if (windKph != null) {
                    append(", wind ")
                    append(formatInt(windKph))
                    append(" km/h")
                }
                append(".")
            }
        }
    }

    // --- Forecast ---

    private fun formatForecast(data: String, japanese: Boolean): String? {
        if (data.isBlank()) return null
        val entries = splitObjectArray(data)
        if (entries.isEmpty()) {
            return if (japanese) "予報はありません。" else "No forecast available."
        }
        val first = entries.first()
        val date = stringField(first, "date")
        val minC = numberField(first, "min_c")
        val maxC = numberField(first, "max_c")
        val condition = stringField(first, "condition")

        return if (japanese) {
            buildString {
                if (!date.isNullOrBlank()) {
                    append(date)
                    append("は")
                }
                if (!condition.isNullOrBlank()) {
                    append(condition)
                }
                if (minC != null && maxC != null) {
                    if (!condition.isNullOrBlank()) append("、")
                    append("最低")
                    append(formatInt(minC))
                    append("度、最高")
                    append(formatInt(maxC))
                    append("度")
                }
                append("。")
            }
        } else {
            buildString {
                if (!date.isNullOrBlank()) {
                    append("On ")
                    append(date)
                    append(", ")
                }
                if (!condition.isNullOrBlank()) {
                    append("expect ")
                    append(condition.lowercase())
                }
                if (minC != null && maxC != null) {
                    if (!condition.isNullOrBlank()) append(", ")
                    append("low ")
                    append(formatInt(minC))
                    append(", high ")
                    append(formatInt(maxC))
                    append(" degrees")
                }
                append(".")
            }
        }
    }

    // --- Web search ---

    private fun formatWebSearch(data: String, japanese: Boolean): String? {
        if (data.isBlank()) return null
        val abstract = stringField(data, "abstract")
        if (!abstract.isNullOrBlank()) return abstract

        val related = stringArrayField(data, "related")
        if (related.isNotEmpty()) return related.first()

        return if (japanese) "検索結果が見つかりませんでした。"
        else "I couldn't find any results for that."
    }

    // --- News ---

    private fun formatNews(data: String, japanese: Boolean): String? {
        if (data.isBlank()) return null
        val entries = splitObjectArray(data)
        if (entries.isEmpty()) {
            return if (japanese) "ニュースは見つかりませんでした。" else "No news right now."
        }
        val titles = entries.take(3).mapNotNull { stringField(it, "title") }
        if (titles.isEmpty()) return null

        return if (japanese) {
            "今日のニュース: " + titles.joinToString("。 ") + "。"
        } else {
            "Today's top headlines: " + titles.joinToString(". ") + "."
        }
    }

    // --- Parsing primitives (regex-based, forgiving) ---

    /** Matches `"key":"value"` and returns the unescaped string value. */
    private fun stringField(json: String, key: String): String? {
        val pattern = Regex("\"" + Regex.escape(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        val match = pattern.find(json) ?: return null
        return unescapeJsonString(match.groupValues[1])
    }

    /** Matches `"key":123.4` (number or null) and returns Double, null on null/missing. */
    private fun numberField(json: String, key: String): Double? {
        val pattern = Regex("\"" + Regex.escape(key) + "\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)")
        val match = pattern.find(json) ?: return null
        return match.groupValues[1].toDoubleOrNull()
    }

    /** Extracts `"key":["a","b"]` as a list of strings. */
    private fun stringArrayField(json: String, key: String): List<String> {
        val pattern = Regex("\"" + Regex.escape(key) + "\"\\s*:\\s*\\[([^\\]]*)\\]")
        val arrayBody = pattern.find(json)?.groupValues?.get(1) ?: return emptyList()
        val itemPattern = Regex("\"((?:\\\\.|[^\"\\\\])*)\"")
        return itemPattern.findAll(arrayBody).map { unescapeJsonString(it.groupValues[1]) }.toList()
    }

    /**
     * Very small top-level array splitter. Input looks like
     * `[{...},{...}]`; we return the inner object strings without their
     * surrounding braces stripped (so each is still valid JSON for
     * [stringField] / [numberField]).
     *
     * Tracks brace depth so nested objects don't confuse the split, and
     * ignores braces inside string literals.
     */
    private fun splitObjectArray(json: String): List<String> {
        val trimmed = json.trim()
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return emptyList()
        val body = trimmed.substring(1, trimmed.length - 1)
        val out = mutableListOf<String>()
        var depth = 0
        var inString = false
        var escape = false
        var start = -1
        for (i in body.indices) {
            val c = body[i]
            if (escape) {
                escape = false
                continue
            }
            if (c == '\\') {
                escape = true
                continue
            }
            if (c == '"') {
                inString = !inString
                continue
            }
            if (inString) continue
            when (c) {
                '{' -> {
                    if (depth == 0) start = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && start >= 0) {
                        out.add(body.substring(start, i + 1))
                        start = -1
                    }
                }
            }
        }
        return out
    }

    private fun unescapeJsonString(s: String): String =
        s.replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\n", " ")
            .replace("\\r", " ")
            .replace("\\t", " ")

    /** Drop trailing `.0` so "18.0" speaks as "18". */
    private fun formatInt(n: Double): String {
        val rounded = n.toLong()
        return if (n == rounded.toDouble()) rounded.toString() else n.toString()
    }
}
