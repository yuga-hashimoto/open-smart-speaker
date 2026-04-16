package com.opensmarthome.speaker.tool.info

import com.opensmarthome.speaker.tool.ToolCall
import com.opensmarthome.speaker.tool.ToolExecutor
import com.opensmarthome.speaker.tool.ToolParameter
import com.opensmarthome.speaker.tool.ToolResult
import com.opensmarthome.speaker.tool.ToolSchema
import timber.log.Timber

class CalculatorToolExecutor(
    private val evaluator: MathEvaluator = MathEvaluator()
) : ToolExecutor {

    override suspend fun availableTools(): List<ToolSchema> = listOf(
        ToolSchema(
            name = "calculate",
            description = "Safely evaluate an arithmetic expression. Supports + - * / % ^ with parentheses and sqrt, abs, round, floor, ceil functions. Example: '(5+3) * sqrt(16)'.",
            parameters = mapOf(
                "expression" to ToolParameter("string", "Arithmetic expression", required = true)
            )
        )
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        return try {
            when (call.name) {
                "calculate" -> executeCalculate(call)
                else -> ToolResult(call.id, false, "", "Unknown tool: ${call.name}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Calculator tool failed")
            ToolResult(call.id, false, "", e.message ?: "Calculation failed")
        }
    }

    private fun executeCalculate(call: ToolCall): ToolResult {
        val expr = call.arguments["expression"] as? String
            ?: return ToolResult(call.id, false, "", "Missing expression")

        return when (val r = evaluator.eval(expr)) {
            is MathEvaluator.Result.Ok -> ToolResult(
                call.id, true,
                """{"result":${"%.6f".format(r.value)}}"""
            )
            is MathEvaluator.Result.ParseError ->
                ToolResult(call.id, false, "", "Parse error: ${r.reason}")
            is MathEvaluator.Result.EvalError ->
                ToolResult(call.id, false, "", "Eval error: ${r.reason}")
        }
    }
}
