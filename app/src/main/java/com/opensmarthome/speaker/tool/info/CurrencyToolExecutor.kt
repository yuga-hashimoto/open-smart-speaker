package com.opensmarthome.speaker.tool.info

import com.opensmarthome.speaker.tool.ToolCall
import com.opensmarthome.speaker.tool.ToolExecutor
import com.opensmarthome.speaker.tool.ToolParameter
import com.opensmarthome.speaker.tool.ToolResult
import com.opensmarthome.speaker.tool.ToolSchema
import timber.log.Timber

class CurrencyToolExecutor(
    private val converter: CurrencyConverter = CurrencyConverter()
) : ToolExecutor {

    override suspend fun availableTools(): List<ToolSchema> = listOf(
        ToolSchema(
            name = "set_currency_rate",
            description = "Teach the agent a currency rate. Rate is expressed in base units (USD). For EUR worth 1.08 USD, pass currency='EUR', rate_usd=1.08.",
            parameters = mapOf(
                "currency" to ToolParameter("string", "ISO code of the currency (e.g. EUR, JPY)", required = true),
                "rate_usd" to ToolParameter("number", "How many USD one unit of this currency is worth", required = true)
            )
        ),
        ToolSchema(
            name = "convert_currency",
            description = "Convert an amount between two currencies using previously taught rates. If a rate is unknown, tell the user and offer to look it up via web_search then save via set_currency_rate.",
            parameters = mapOf(
                "amount" to ToolParameter("number", "Amount to convert", required = true),
                "from" to ToolParameter("string", "Source currency code", required = true),
                "to" to ToolParameter("string", "Target currency code", required = true)
            )
        ),
        ToolSchema(
            name = "list_currencies",
            description = "List currencies the agent currently knows rates for.",
            parameters = emptyMap()
        )
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        return try {
            when (call.name) {
                "set_currency_rate" -> executeSet(call)
                "convert_currency" -> executeConvert(call)
                "list_currencies" -> executeList(call)
                else -> ToolResult(call.id, false, "", "Unknown tool: ${call.name}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Currency tool failed")
            ToolResult(call.id, false, "", e.message ?: "Currency error")
        }
    }

    private fun executeSet(call: ToolCall): ToolResult {
        val currency = call.arguments["currency"] as? String
            ?: return ToolResult(call.id, false, "", "Missing currency")
        val rate = (call.arguments["rate_usd"] as? Number)?.toDouble()
            ?: return ToolResult(call.id, false, "", "Missing rate_usd")
        if (rate <= 0) return ToolResult(call.id, false, "", "rate_usd must be positive")

        converter.setRate(currency, rate)
        return ToolResult(call.id, true, """{"set":"${currency.uppercase()}","rate_usd":$rate}""")
    }

    private fun executeConvert(call: ToolCall): ToolResult {
        val amount = (call.arguments["amount"] as? Number)?.toDouble()
            ?: return ToolResult(call.id, false, "", "Missing amount")
        val from = call.arguments["from"] as? String
            ?: return ToolResult(call.id, false, "", "Missing from")
        val to = call.arguments["to"] as? String
            ?: return ToolResult(call.id, false, "", "Missing to")

        return when (val r = converter.convert(amount, from, to)) {
            is CurrencyConverter.Result.Converted -> ToolResult(
                call.id, true,
                """{"amount":${"%.4f".format(r.value)},"from":"${r.from}","to":"${r.to}"}"""
            )
            is CurrencyConverter.Result.UnknownRate -> ToolResult(
                call.id, false, "",
                "No rate for ${r.currency}. Teach it with set_currency_rate or look it up via web_search."
            )
        }
    }

    private fun executeList(call: ToolCall): ToolResult {
        val codes = converter.knownCurrencies().joinToString(",") { "\"$it\"" }
        return ToolResult(call.id, true, "[$codes]")
    }
}
