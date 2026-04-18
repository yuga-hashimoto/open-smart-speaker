package com.opendash.app.tool.info

import com.opendash.app.tool.ToolCall
import com.opendash.app.tool.ToolExecutor
import com.opendash.app.tool.ToolParameter
import com.opendash.app.tool.ToolResult
import com.opendash.app.tool.ToolSchema
import timber.log.Timber

class UnitConverterToolExecutor(
    private val converter: UnitConverter = UnitConverter()
) : ToolExecutor {

    override suspend fun availableTools(): List<ToolSchema> = listOf(
        ToolSchema(
            name = "convert_units",
            description = "Convert a value from one unit to another. Supports length (m/km/cm/mm/mile/foot/inch/yard), mass (g/kg/mg/lb/oz), temperature (c/f/k), volume (l/ml/cup/gallon/floz).",
            parameters = mapOf(
                "value" to ToolParameter("number", "The value to convert", required = true),
                "from" to ToolParameter("string", "Source unit name", required = true),
                "to" to ToolParameter("string", "Target unit name", required = true)
            )
        )
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        return try {
            when (call.name) {
                "convert_units" -> executeConvert(call)
                else -> ToolResult(call.id, false, "", "Unknown tool: ${call.name}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Unit converter failed")
            ToolResult(call.id, false, "", e.message ?: "Conversion failed")
        }
    }

    private fun executeConvert(call: ToolCall): ToolResult {
        val value = (call.arguments["value"] as? Number)?.toDouble()
            ?: return ToolResult(call.id, false, "", "Missing numeric value")
        val from = call.arguments["from"] as? String
            ?: return ToolResult(call.id, false, "", "Missing from unit")
        val to = call.arguments["to"] as? String
            ?: return ToolResult(call.id, false, "", "Missing to unit")

        return when (val result = converter.convert(value, from, to)) {
            is UnitConverter.Result.Converted -> ToolResult(
                call.id, true,
                """{"value":${"%.6f".format(result.value)},"unit":"${result.unit}"}"""
            )
            is UnitConverter.Result.UnknownUnit -> ToolResult(
                call.id, false, "", "Unknown unit: ${result.unit}"
            )
            is UnitConverter.Result.IncompatibleDimensions -> ToolResult(
                call.id, false, "",
                "Cannot convert ${result.from} to ${result.to} (different dimensions)"
            )
        }
    }
}
