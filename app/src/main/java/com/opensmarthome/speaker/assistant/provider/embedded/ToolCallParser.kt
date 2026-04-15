package com.opensmarthome.speaker.assistant.provider.embedded

import com.opensmarthome.speaker.assistant.model.ToolCallRequest
import timber.log.Timber

/**
 * Parses LLM output to extract tool calls and plain text.
 *
 * Supports two formats:
 * - New: {"tool_call": {"name": "...", "arguments": {...}}}
 * - Legacy: {"tool": "...", "arguments": {...}}
 */
class ToolCallParser {

    data class ParseResult(
        val text: String,
        val toolCalls: List<ToolCallRequest>
    )

    fun parse(response: String): ParseResult {
        if (response.isBlank()) return ParseResult("", emptyList())

        val toolCalls = mutableListOf<ToolCallRequest>()
        val textParts = mutableListOf<String>()

        val lines = response.lines()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            val toolCall = tryParseToolCall(trimmed)
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

    private fun tryParseToolCall(line: String): ToolCallRequest? {
        if (!line.startsWith("{")) return null

        return tryParseNewFormat(line) ?: tryParseLegacyFormat(line)
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
                        // Basic validation: must contain at least a quoted key
                        return if (extracted.contains("\"")) extracted else null
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

        private val ARGUMENTS_KEY_REGEX =
            """"arguments"\s*:\s*""".toRegex()
    }
}
