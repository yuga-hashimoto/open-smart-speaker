package com.opensmarthome.speaker.assistant.provider.embedded

import com.opensmarthome.speaker.assistant.model.ToolCallRequest
import timber.log.Timber

/**
 * Parses LLM output to extract tool calls and plain text.
 *
 * Tolerant multi-format parsing — small on-device models often emit one of
 * several tool-call shapes and we accept them all (stolen from
 * off-grid-mobile-ai multi-format pattern):
 *
 * - JSON new:      {"tool_call": {"name": "...", "arguments": {...}}}
 * - JSON legacy:   {"tool": "...", "arguments": {...}}
 * - Single-level:  {"name": "...", "arguments": {...}}      (bare form)
 * - XML wrapper:   <tool_call>{"name": "...", "arguments": {...}}</tool_call>
 * - Gemma 4 style: <|tool_call>{"name": "...", "arguments": {...}}<tool_call|>
 * - Hybrid tag:    <|tool_call>call:name{key: "value"}<tool_call|>
 *                  <tool_call>name(key="value")</tool_call>
 *   (Gemma 4 E2B frequently invents this when it copies few-shot syntax.)
 * - Natural form:  TOOL_CALL: name(key="value", key2=42)
 * - Markdown fenced JSON (```json ... ```) is also tolerated.
 *
 * XML tokens and TOOL_CALL lines are stripped from the visible text output.
 */
class ToolCallParser {

    data class ParseResult(
        val text: String,
        val toolCalls: List<ToolCallRequest>
    )

    fun parse(response: String): ParseResult {
        if (response.isBlank()) return ParseResult("", emptyList())

        val toolCalls = mutableListOf<ToolCallRequest>()

        // Strip markdown code fences before parsing (```json, ``` etc.).
        var cleaned = stripCodeFences(response)

        // First: hybrid-tag format, e.g. `<|tool_call>call:name{args}<tool_call|>`.
        // Small on-device models (Gemma 4 E2B) emit this when they copy
        // few-shot syntax. We run it before the generic XML pass because the
        // inner payload isn't valid JSON. When any hybrid body matches we
        // also strip lingering orphan closers (`<tool_call|>`, `<tool...`)
        // that the main regex couldn't consume because they appeared after
        // the body with intervening text.
        var hybridMatched = false
        for (match in HYBRID_TAG_REGEX.findAll(cleaned)) {
            hybridMatched = true
            parseHybridTagMatch(match.groupValues[1], match.groupValues[2])?.let {
                toolCalls.add(it)
            }
        }
        cleaned = cleaned.replace(HYBRID_TAG_REGEX, "")
        if (hybridMatched) {
            cleaned = cleaned.replace(HYBRID_TRAILING_CLOSER_REGEX, "")
        }

        // Next: extract XML/Gemma-style tool calls (may span multiple lines)
        for (xmlRegex in XML_REGEXES) {
            xmlRegex.findAll(cleaned).forEach { match ->
                val inner = match.groupValues[1].trim()
                tryParseJson(inner)?.let { toolCalls.add(it) }
            }
            cleaned = cleaned.replace(xmlRegex, "")
        }

        // Line-by-line parsing on the remainder: JSON (various shapes) and
        // natural-language TOOL_CALL: name(args) form.
        val textParts = mutableListOf<String>()
        for (line in cleaned.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            val toolCall = tryParseToolCall(trimmed) ?: tryParseNaturalCall(trimmed)
            if (toolCall != null) {
                toolCalls.add(toolCall)
            } else {
                textParts.add(line)
            }
        }

        return ParseResult(
            text = textParts.joinToString("\n").trim(),
            toolCalls = toolCalls
        )
    }

    private fun stripCodeFences(input: String): String {
        // Remove ```<lang>\n and closing ``` markers without touching content.
        return input
            .replace(FENCE_OPEN_REGEX, "")
            .replace(FENCE_CLOSE_REGEX, "")
    }

    private fun tryParseToolCall(line: String): ToolCallRequest? {
        if (!line.startsWith("{")) return null

        // Accept new wrapper, legacy shape, or bare single-level form.
        return tryParseNewFormat(line)
            ?: tryParseLegacyFormat(line)
            ?: tryParseBareFormat(line)
    }

    /**
     * Natural-language form: `TOOL_CALL: name(arg1="foo", arg2=42)`.
     * Useful because tiny instruction-tuned models emit this shape by default.
     */
    private fun tryParseNaturalCall(line: String): ToolCallRequest? {
        val match = NATURAL_CALL_REGEX.find(line) ?: return null
        val name = match.groupValues[1]
        if (name.isBlank()) return null
        val rawArgs = match.groupValues[2].trim()
        val arguments = if (rawArgs.isEmpty()) "{}" else naturalArgsToJson(rawArgs)
        return ToolCallRequest(
            id = "call_${System.currentTimeMillis()}",
            name = name,
            arguments = arguments
        )
    }

    /**
     * Parse the hybrid-tag match.
     *
     * @param name Tool name captured between `(call:)?` and the opening
     *     bracket. May include leading/trailing whitespace.
     * @param rawArgs The body between the brackets. Accepts either
     *     `key="value", n=42` (function-call form, same as natural form)
     *     or `key: "value", n: 42` (JSON-object-lite form, which is what
     *     Gemma actually emits most often).
     */
    private fun parseHybridTagMatch(name: String, rawArgs: String): ToolCallRequest? {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) return null
        val args = rawArgs.trim()
        val arguments = when {
            args.isEmpty() -> "{}"
            // JSON-lite: `key: "val"` style — normalize colons to equals then
            // reuse the natural-call converter. Distinguishing token is the
            // absence of `=` combined with presence of `:` at top level.
            containsTopLevelColon(args) && !containsTopLevelEquals(args) ->
                jsonLiteArgsToJson(args)
            else -> naturalArgsToJson(args)
        }
        return ToolCallRequest(
            id = "call_${System.currentTimeMillis()}",
            name = trimmedName,
            arguments = arguments
        )
    }

    /**
     * Convert `key: "val", num: 42` → `{"key":"val","num":42}`. Works on the
     * JSON-object-lite form that `<|tool_call>` bodies often use. The split is
     * done at the top level only, so nested `{...}` / `[...]` / quoted
     * strings containing colons or commas survive.
     */
    private fun jsonLiteArgsToJson(raw: String): String {
        val pairs = splitTopLevelCommas(raw)
        if (pairs.isEmpty()) return "{}"
        val sb = StringBuilder("{")
        var first = true
        for (pair in pairs) {
            val colon = topLevelColonIndex(pair)
            if (colon == -1) continue
            val key = pair.substring(0, colon).trim().trim('"', '\'')
            val value = pair.substring(colon + 1).trim()
            if (key.isEmpty()) continue
            if (!first) sb.append(",")
            first = false
            sb.append('"').append(escapeJson(key)).append('"').append(':')
            sb.append(normalizeValue(value))
        }
        sb.append('}')
        return sb.toString()
    }

    /** True when [raw] has a `:` outside of strings/brackets. */
    private fun containsTopLevelColon(raw: String): Boolean =
        topLevelColonIndex(raw) != -1

    /** True when [raw] has an `=` outside of strings/brackets. */
    private fun containsTopLevelEquals(raw: String): Boolean {
        var depth = 0
        var inString = false
        var stringChar = '"'
        for (c in raw) {
            when {
                inString -> if (c == stringChar) inString = false
                c == '"' || c == '\'' -> { inString = true; stringChar = c }
                c == '(' || c == '[' || c == '{' -> depth++
                c == ')' || c == ']' || c == '}' -> depth--
                c == '=' && depth == 0 -> return true
            }
        }
        return false
    }

    /** First top-level `:` index, or -1 if none. */
    private fun topLevelColonIndex(raw: String): Int {
        var depth = 0
        var inString = false
        var stringChar = '"'
        for (i in raw.indices) {
            val c = raw[i]
            when {
                inString -> if (c == stringChar) inString = false
                c == '"' || c == '\'' -> { inString = true; stringChar = c }
                c == '(' || c == '[' || c == '{' -> depth++
                c == ')' || c == ']' || c == '}' -> depth--
                c == ':' && depth == 0 -> return i
            }
        }
        return -1
    }

    /**
     * Convert `key="val", num=42, flag=true` → `{"key":"val","num":42,"flag":true}`.
     * Leaves quoted strings untouched; numeric/boolean literals pass through.
     * Unquoted bare identifiers are quoted as strings.
     */
    private fun naturalArgsToJson(raw: String): String {
        val pairs = splitTopLevelCommas(raw)
        if (pairs.isEmpty()) return "{}"
        val sb = StringBuilder("{")
        var first = true
        for (pair in pairs) {
            val eq = pair.indexOf('=')
            if (eq == -1) continue
            val key = pair.substring(0, eq).trim().trim('"', '\'')
            val value = pair.substring(eq + 1).trim()
            if (key.isEmpty()) continue
            if (!first) sb.append(",")
            first = false
            sb.append('"').append(escapeJson(key)).append('"').append(':')
            sb.append(normalizeValue(value))
        }
        sb.append('}')
        return sb.toString()
    }

    private fun splitTopLevelCommas(raw: String): List<String> {
        val out = mutableListOf<String>()
        var depth = 0
        var inString = false
        var stringChar = '"'
        val current = StringBuilder()
        for (c in raw) {
            when {
                inString -> {
                    current.append(c)
                    if (c == stringChar) inString = false
                }
                c == '"' || c == '\'' -> {
                    inString = true
                    stringChar = c
                    current.append(c)
                }
                c == '(' || c == '[' || c == '{' -> {
                    depth++
                    current.append(c)
                }
                c == ')' || c == ']' || c == '}' -> {
                    depth--
                    current.append(c)
                }
                c == ',' && depth == 0 -> {
                    out.add(current.toString())
                    current.clear()
                }
                else -> current.append(c)
            }
        }
        if (current.isNotEmpty()) out.add(current.toString())
        return out
    }

    private fun normalizeValue(raw: String): String {
        val v = raw.trim()
        if (v.isEmpty()) return "\"\""
        // Already a JSON literal (quoted string, object, array, bool, null, number).
        if (v.startsWith("\"") && v.endsWith("\"")) return v
        if (v.startsWith("'") && v.endsWith("'")) {
            // Convert single-quoted to double-quoted JSON string.
            return "\"" + escapeJson(v.substring(1, v.length - 1)) + "\""
        }
        if (v == "true" || v == "false" || v == "null") return v
        if (NUMBER_REGEX.matches(v)) return v
        if (v.startsWith("{") || v.startsWith("[")) return v
        // Bare identifier: quote it.
        return "\"" + escapeJson(v) + "\""
    }

    private fun escapeJson(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"")

    /**
     * Parse a JSON blob that may be either new, legacy, or a bare {name, arguments} form.
     * Used for content extracted from XML wrappers.
     */
    private fun tryParseJson(json: String): ToolCallRequest? {
        val trimmed = json.trim()
        if (!trimmed.startsWith("{")) return null

        // Try formats in priority order
        tryParseNewFormat(trimmed)?.let { return it }
        tryParseLegacyFormat(trimmed)?.let { return it }
        // Bare {name, arguments} — used inside XML wrappers
        return tryParseBareFormat(trimmed)
    }

    /**
     * Bare format (used inside XML wrappers): {"name": "...", "arguments": {...}}
     */
    private fun tryParseBareFormat(line: String): ToolCallRequest? {
        val match = BARE_NAME_REGEX.find(line) ?: return null
        return try {
            val name = match.groupValues[1]
            val arguments = extractArguments(line, match.range.last) ?: return null
            if (name.isNotBlank()) {
                ToolCallRequest(
                    id = "call_${System.currentTimeMillis()}",
                    name = name,
                    arguments = arguments
                )
            } else null
        } catch (e: Exception) {
            Timber.d("Failed to parse bare format tool call: $line")
            null
        }
    }

    /**
     * New format: {"tool_call": {"name": "...", "arguments": {...}}}
     */
    private fun tryParseNewFormat(line: String): ToolCallRequest? {
        val match = NEW_FORMAT_REGEX.find(line) ?: return null
        return try {
            val name = match.groupValues[1]
            val arguments = extractArguments(line, match.range.last) ?: return null
            if (name.isNotBlank()) {
                ToolCallRequest(
                    id = "call_${System.currentTimeMillis()}",
                    name = name,
                    arguments = arguments
                )
            } else null
        } catch (e: Exception) {
            Timber.d("Failed to parse new format tool call: $line")
            null
        }
    }

    /**
     * Legacy format: {"tool": "...", "arguments": {...}}
     */
    private fun tryParseLegacyFormat(line: String): ToolCallRequest? {
        val match = LEGACY_FORMAT_REGEX.find(line) ?: return null
        return try {
            val name = match.groupValues[1]
            val arguments = extractArguments(line, match.range.last) ?: return null
            if (name.isNotBlank()) {
                ToolCallRequest(
                    id = "call_${System.currentTimeMillis()}",
                    name = name,
                    arguments = arguments
                )
            } else null
        } catch (e: Exception) {
            Timber.d("Failed to parse legacy format tool call: $line")
            null
        }
    }

    /**
     * Extract the "arguments" JSON object from the line, handling nested braces.
     * Returns null if no valid JSON object is found.
     */
    private fun extractArguments(line: String, searchStart: Int): String? {
        val argsMatch = ARGUMENTS_KEY_REGEX.find(line, searchStart.coerceAtLeast(0))
            ?: return null

        val braceStart = line.indexOf('{', argsMatch.range.last)
        if (braceStart == -1) return null

        var depth = 0
        for (i in braceStart until line.length) {
            when (line[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        val extracted = line.substring(braceStart, i + 1)
                        // Empty object {} is valid. Otherwise must contain a quoted key.
                        return if (extracted == "{}" || extracted.contains("\"")) extracted else null
                    }
                }
            }
        }

        return null // Unbalanced braces
    }

    companion object {
        private val NEW_FORMAT_REGEX =
            """"tool_call"\s*:\s*\{\s*"name"\s*:\s*"(\w+)"""".toRegex()

        private val LEGACY_FORMAT_REGEX =
            """"tool"\s*:\s*"(\w+)"""".toRegex()

        private val BARE_NAME_REGEX =
            """"name"\s*:\s*"(\w+)"""".toRegex()

        private val ARGUMENTS_KEY_REGEX =
            """"arguments"\s*:\s*""".toRegex()

        // Natural-language form: TOOL_CALL: name(arg=value, ...)
        // Case-insensitive TOOL_CALL marker; tool name must be snake_case-ish.
        private val NATURAL_CALL_REGEX =
            """(?i)\btool_call\s*:\s*([a-zA-Z_][a-zA-Z0-9_]*)\s*\(([^)]*)\)""".toRegex()

        private val NUMBER_REGEX = """-?\d+(\.\d+)?""".toRegex()

        // Strip ```json, ```kotlin, ``` etc. fences that some models wrap output in.
        private val FENCE_OPEN_REGEX = """(?m)^\s*```[a-zA-Z0-9_-]*\s*$""".toRegex()
        private val FENCE_CLOSE_REGEX = """(?m)^\s*```\s*$""".toRegex()

        // XML-style wrappers (content is a JSON body).
        // Using DOT_MATCHES_ALL so the inner JSON can span multiple lines.
        private val XML_REGEXES = listOf(
            // Standard: <tool_call>...</tool_call>
            """<tool_call>([\s\S]*?)</tool_call>""".toRegex(),
            // Gemma 4 style: <|tool_call>...<tool_call|>
            """<\|tool_call>([\s\S]*?)<tool_call\|>""".toRegex()
        )

        /**
         * Hybrid-tag: `<|?tool_call|?>(call:)?name{args}` or `(args)`.
         *
         * - Accepts `<tool_call>` or `<|tool_call>` as the opening.
         * - The optional `call:` prefix is what Gemma actually emits.
         * - Name is a snake-case identifier.
         * - Args body is captured lazily up to the matching closing `}` or `)`.
         * - A trailing closing tag (`<tool_call|>`, `</tool_call>`, `<tool...`)
         *   is optional because the model frequently truncates or garbles it.
         *
         * Example: `<|tool_call>call:web_search{query: "トマト ウェブ"}<tool...`
         */
        internal val HYBRID_TAG_REGEX =
            """<\|?tool_call\|?>\s*(?:call:)?\s*([a-zA-Z_][a-zA-Z0-9_]*)\s*[({]([^)}]*)[)}]""".toRegex()

        /**
         * Lingering close tags left behind after the primary hybrid match.
         * Only applied when a hybrid body was matched, so `<toolbox>` in
         * normal prose is safe.
         *
         * - `<tool_call|>` (Gemma pipe-style closer)
         * - `</tool_call>` (well-formed closer — sometimes stand-alone)
         * - `<|tool_call>` (pipe-style opener without a matching body)
         * - `<tool_call>` (plain opener without a matching body)
         * - `<tool_call...` / `<tool...` truncated closers — we only match
         *   this shape when the `tool_call` prefix or the literal ellipsis
         *   is present to keep the pattern conservative.
         */
        internal val HYBRID_TRAILING_CLOSER_REGEX =
            (
                """(?:<tool_call\|>|</tool_call>|<\|tool_call>|<tool_call>""" +
                    """|<tool_call\S*|<tool\.{2,})"""
            ).toRegex()

        /**
         * Phrases that signal the model is refusing or hallucinating "I have
         * no tools" — a trigger to re-prompt with a stricter directive.
         * Kept lowercase; caller should compare against a lowercased string.
         */
        private val REFUSAL_MARKERS = listOf(
            // English — refusals and "I don't know" hallucinations
            "i don't have tools", "i don't have access", "i can't", "i cannot",
            "i'm unable", "i am unable", "i do not have", "i don't have the ability",
            "as an ai", "i'm just", "not able to",
            "i don't know", "i do not know", "i'm not sure", "i am not sure",
            "i apologize", "i'm sorry, but", "sorry, but i",
            "unfortunately, i", "unfortunately i",
            // Japanese — refusals and "分からない" hallucinations
            "できません", "できない", "持っていません", "持ってません", "持ちません",
            "ツールがありません", "ツールを持って", "対応できません", "無理です",
            "わかりません", "分かりません", "わからない", "分からない",
            "知りません", "存じません",
            "申し訳"
        )

        /**
         * Returns true if [response] contains a refusal marker. Used by the
         * provider to decide whether to re-prompt with a stricter directive.
         */
        fun looksLikeRefusal(response: String): Boolean {
            if (response.isBlank()) return false
            val lower = response.lowercase()
            return REFUSAL_MARKERS.any { it in lower }
        }
    }
}
