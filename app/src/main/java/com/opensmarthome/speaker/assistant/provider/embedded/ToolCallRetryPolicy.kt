package com.opensmarthome.speaker.assistant.provider.embedded

import com.opensmarthome.speaker.assistant.model.AssistantMessage
import com.opensmarthome.speaker.assistant.model.ToolCallRequest
import com.opensmarthome.speaker.tool.ToolSchema

/**
 * Decides whether the on-device LLM's first attempt needs a stricter retry
 * and composes the final `AssistantMessage.Assistant` from the parsed
 * response(s).
 *
 * Pure, injectable, and testable without a real LiteRT-LM engine. The
 * provider supplies the actual `send` side-effect as a suspending lambda.
 */
class ToolCallRetryPolicy(
    private val parser: ToolCallParser = ToolCallParser()
) {

    /**
     * Run the (already-executed) first attempt through parsing + refusal
     * detection and, if needed, call [retryFn] to obtain a second attempt.
     *
     * [retryFn] is only invoked when:
     *  - [tools] is non-empty, AND
     *  - the first attempt did not produce any tool calls, AND
     *  - the first attempt looks like a refusal (see
     *    [ToolCallParser.looksLikeRefusal]).
     *
     * Guarantees at most one retry.
     */
    suspend fun finalize(
        firstAttempt: String,
        tools: List<ToolSchema>,
        retryFn: suspend () -> String
    ): AssistantMessage.Assistant {
        val parsedFirst = parser.parse(firstAttempt)
        if (parsedFirst.toolCalls.isNotEmpty()) {
            return assistant(parsedFirst.text, parsedFirst.toolCalls)
        }

        if (tools.isEmpty() || !ToolCallParser.looksLikeRefusal(firstAttempt)) {
            return assistant(parsedFirst.text.ifBlank { firstAttempt.trim() }, emptyList())
        }

        val retryResponse = retryFn()
        val parsedRetry = parser.parse(retryResponse)
        if (parsedRetry.toolCalls.isNotEmpty()) {
            return assistant(parsedRetry.text, parsedRetry.toolCalls)
        }
        val fallbackText = parsedRetry.text
            .ifBlank { parsedFirst.text.ifBlank { firstAttempt.trim() } }
        return assistant(fallbackText, emptyList())
    }

    private fun assistant(
        content: String,
        toolCalls: List<ToolCallRequest>
    ): AssistantMessage.Assistant =
        AssistantMessage.Assistant(content = content, toolCalls = toolCalls)
}
