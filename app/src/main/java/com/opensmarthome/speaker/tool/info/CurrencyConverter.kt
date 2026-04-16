package com.opensmarthome.speaker.tool.info

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory currency converter using user-taught rates.
 * No external API — rates are taught by the user (or the LLM via web_search),
 * which keeps the app offline-first and CLAUDE.md-compliant.
 *
 * Internally, all rates are stored relative to a single base currency
 * (USD by default). Setting EUR→USD=1.08 and JPY→USD=0.0067 lets us
 * answer EUR→JPY without extra work.
 */
class CurrencyConverter(
    private val base: String = "USD"
) {

    sealed class Result {
        data class Converted(val value: Double, val from: String, val to: String) : Result()
        data class UnknownRate(val currency: String) : Result()
    }

    // Rate of key currency expressed in units of base currency.
    // e.g. rates["EUR"] = 1.08 means 1 EUR = 1.08 USD
    private val rates = ConcurrentHashMap<String, Double>()

    init {
        rates[base.uppercase()] = 1.0
    }

    fun setRate(currency: String, rateInBase: Double) {
        require(rateInBase > 0) { "rate must be positive" }
        rates[currency.uppercase()] = rateInBase
    }

    fun removeRate(currency: String): Boolean =
        currency.uppercase() != base.uppercase() &&
            rates.remove(currency.uppercase()) != null

    fun knownCurrencies(): List<String> = rates.keys.toList().sorted()

    fun convert(value: Double, from: String, to: String): Result {
        val fromUpper = from.uppercase()
        val toUpper = to.uppercase()
        val fromRate = rates[fromUpper] ?: return Result.UnknownRate(from)
        val toRate = rates[toUpper] ?: return Result.UnknownRate(to)

        // value in base, then base in target
        val inBase = value * fromRate
        val inTarget = inBase / toRate
        return Result.Converted(inTarget, fromUpper, toUpper)
    }
}
