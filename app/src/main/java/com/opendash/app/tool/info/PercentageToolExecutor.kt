package com.opendash.app.tool.info

import com.opendash.app.tool.ToolCall
import com.opendash.app.tool.ToolExecutor
import com.opendash.app.tool.ToolParameter
import com.opendash.app.tool.ToolResult
import com.opendash.app.tool.ToolSchema
import timber.log.Timber

/**
 * Three everyday percentage helpers the LLM can reach for without
 * spending a `calculate` round-trip:
 *
 * - `percent_of` — "what is X% of Y" (e.g. 15% of 80 = 12).
 * - `percent_change` — signed delta from before→after as a percentage.
 *   Returns an error when the baseline is zero to avoid divide-by-zero
 *   noise in the envelope.
 * - `tip_calc` — bill × tip%, returning both the tip amount and the
 *   grand total. Tip percent is clamped to [0, 100].
 *
 * All outputs use six fraction digits so the JSON envelope matches the
 * conventions in [CalculatorToolExecutor]. Stdlib only — no network,
 * no state.
 */
class PercentageToolExecutor : ToolExecutor {

    override suspend fun availableTools(): List<ToolSchema> = listOf(
        ToolSchema(
            name = "percent_of",
            description = "Compute X% of Y. Example: percent_of(percent=15, value=80) → 12.",
            parameters = mapOf(
                "percent" to ToolParameter(
                    type = "number",
                    description = "The percentage (e.g. 15 for 15%).",
                    required = true
                ),
                "value" to ToolParameter(
                    type = "number",
                    description = "The base value the percentage is applied to.",
                    required = true
                )
            )
        ),
        ToolSchema(
            name = "percent_change",
            description = "Compute the signed percentage change from 'before' to 'after'. " +
                "Returns positive for growth, negative for shrinkage.",
            parameters = mapOf(
                "before" to ToolParameter(
                    type = "number",
                    description = "The original/baseline value (must be non-zero).",
                    required = true
                ),
                "after" to ToolParameter(
                    type = "number",
                    description = "The new value.",
                    required = true
                )
            )
        ),
        ToolSchema(
            name = "tip_calc",
            description = "Calculate a tip. Returns the tip amount and bill total. " +
                "Tip percent must be in 0..100.",
            parameters = mapOf(
                "bill" to ToolParameter(
                    type = "number",
                    description = "The pre-tip bill total.",
                    required = true
                ),
                "tip_percent" to ToolParameter(
                    type = "number",
                    description = "Tip rate as a percent (e.g. 18 for 18%). 0..100.",
                    required = true
                )
            )
        )
    )

    override suspend fun execute(call: ToolCall): ToolResult = try {
        when (call.name) {
            "percent_of" -> percentOf(call)
            "percent_change" -> percentChange(call)
            "tip_calc" -> tipCalc(call)
            else -> ToolResult(call.id, false, "", "Unknown tool: ${call.name}")
        }
    } catch (e: Exception) {
        Timber.e(e, "Percentage tool failed")
        ToolResult(call.id, false, "", e.message ?: "Percentage tool failed")
    }

    private fun percentOf(call: ToolCall): ToolResult {
        val percent = finiteNumber(call.arguments["percent"])
            ?: return ToolResult(call.id, false, "", "Missing or invalid percent")
        val value = finiteNumber(call.arguments["value"])
            ?: return ToolResult(call.id, false, "", "Missing or invalid value")
        val result = percent / 100.0 * value
        return ToolResult(
            call.id,
            true,
            """{"result":${formatNum(result)}}"""
        )
    }

    private fun percentChange(call: ToolCall): ToolResult {
        val before = finiteNumber(call.arguments["before"])
            ?: return ToolResult(call.id, false, "", "Missing or invalid before")
        val after = finiteNumber(call.arguments["after"])
            ?: return ToolResult(call.id, false, "", "Missing or invalid after")
        if (before == 0.0) {
            return ToolResult(call.id, false, "", "before must be non-zero")
        }
        val pct = (after - before) / before * 100.0
        return ToolResult(
            call.id,
            true,
            """{"percent_change":${formatNum(pct)}}"""
        )
    }

    private fun tipCalc(call: ToolCall): ToolResult {
        val bill = finiteNumber(call.arguments["bill"])
            ?: return ToolResult(call.id, false, "", "Missing or invalid bill")
        val tipPercent = finiteNumber(call.arguments["tip_percent"])
            ?: return ToolResult(call.id, false, "", "Missing or invalid tip_percent")
        if (tipPercent < 0.0) {
            return ToolResult(call.id, false, "", "tip_percent must be non-negative")
        }
        if (tipPercent > 100.0) {
            return ToolResult(call.id, false, "", "tip_percent must not exceed 100")
        }
        val tip = bill * tipPercent / 100.0
        val total = bill + tip
        return ToolResult(
            call.id,
            true,
            """{"tip":${formatNum(tip)},"total":${formatNum(total)}}"""
        )
    }

    private fun finiteNumber(raw: Any?): Double? {
        val v = when (raw) {
            is Number -> raw.toDouble()
            is String -> raw.toDoubleOrNull()
            else -> null
        } ?: return null
        if (v.isNaN() || v.isInfinite()) return null
        return v
    }

    private fun formatNum(v: Double): String = "%.6f".format(v)
}
