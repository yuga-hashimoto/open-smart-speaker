package com.opendash.app.tool.info

/**
 * Converts common measurement units without any external API.
 * Supported dimensions: length, mass, temperature, volume.
 */
class UnitConverter {

    sealed class Result {
        data class Converted(val value: Double, val unit: String) : Result()
        data class UnknownUnit(val unit: String) : Result()
        data class IncompatibleDimensions(val from: String, val to: String) : Result()
    }

    fun convert(value: Double, fromUnit: String, toUnit: String): Result {
        val from = canonicalize(fromUnit) ?: return Result.UnknownUnit(fromUnit)
        val to = canonicalize(toUnit) ?: return Result.UnknownUnit(toUnit)
        if (from.dimension != to.dimension) {
            return Result.IncompatibleDimensions(fromUnit, toUnit)
        }

        // Temperature has additive conversions; compute via Celsius.
        if (from.dimension == Dimension.TEMPERATURE) {
            val celsius = when (from.key) {
                "c" -> value
                "f" -> (value - 32) * 5.0 / 9.0
                "k" -> value - 273.15
                else -> return Result.UnknownUnit(fromUnit)
            }
            val result = when (to.key) {
                "c" -> celsius
                "f" -> celsius * 9.0 / 5.0 + 32
                "k" -> celsius + 273.15
                else -> return Result.UnknownUnit(toUnit)
            }
            return Result.Converted(result, to.key)
        }

        // Linear conversion via canonical factor.
        val canonical = value * from.toCanonical
        val out = canonical / to.toCanonical
        return Result.Converted(out, to.key)
    }

    private enum class Dimension { LENGTH, MASS, TEMPERATURE, VOLUME }

    private data class Unit(val key: String, val dimension: Dimension, val toCanonical: Double)

    private fun canonicalize(raw: String): Unit? {
        val k = raw.lowercase().trim().trimEnd('s')
        return UNITS[k] ?: UNITS[k + "s"] ?: UNITS[k.removeSuffix("es")]
    }

    private val UNITS: Map<String, Unit> = buildMap {
        // length (canonical: meter)
        put("m", Unit("m", Dimension.LENGTH, 1.0))
        put("meter", Unit("m", Dimension.LENGTH, 1.0))
        put("km", Unit("km", Dimension.LENGTH, 1000.0))
        put("kilometer", Unit("km", Dimension.LENGTH, 1000.0))
        put("cm", Unit("cm", Dimension.LENGTH, 0.01))
        put("mm", Unit("mm", Dimension.LENGTH, 0.001))
        put("mile", Unit("mile", Dimension.LENGTH, 1609.344))
        put("mi", Unit("mile", Dimension.LENGTH, 1609.344))
        put("foot", Unit("foot", Dimension.LENGTH, 0.3048))
        put("ft", Unit("foot", Dimension.LENGTH, 0.3048))
        put("inch", Unit("inch", Dimension.LENGTH, 0.0254))
        put("in", Unit("inch", Dimension.LENGTH, 0.0254))
        put("yard", Unit("yard", Dimension.LENGTH, 0.9144))
        put("yd", Unit("yard", Dimension.LENGTH, 0.9144))

        // mass (canonical: gram)
        put("g", Unit("g", Dimension.MASS, 1.0))
        put("gram", Unit("g", Dimension.MASS, 1.0))
        put("kg", Unit("kg", Dimension.MASS, 1000.0))
        put("kilogram", Unit("kg", Dimension.MASS, 1000.0))
        put("mg", Unit("mg", Dimension.MASS, 0.001))
        put("lb", Unit("lb", Dimension.MASS, 453.59237))
        put("pound", Unit("lb", Dimension.MASS, 453.59237))
        put("oz", Unit("oz", Dimension.MASS, 28.349523125))
        put("ounce", Unit("oz", Dimension.MASS, 28.349523125))

        // temperature (additive; factors unused)
        put("c", Unit("c", Dimension.TEMPERATURE, 1.0))
        put("celsius", Unit("c", Dimension.TEMPERATURE, 1.0))
        put("f", Unit("f", Dimension.TEMPERATURE, 1.0))
        put("fahrenheit", Unit("f", Dimension.TEMPERATURE, 1.0))
        put("k", Unit("k", Dimension.TEMPERATURE, 1.0))
        put("kelvin", Unit("k", Dimension.TEMPERATURE, 1.0))

        // volume (canonical: liter)
        put("l", Unit("l", Dimension.VOLUME, 1.0))
        put("liter", Unit("l", Dimension.VOLUME, 1.0))
        put("ml", Unit("ml", Dimension.VOLUME, 0.001))
        put("cup", Unit("cup", Dimension.VOLUME, 0.236588))
        put("gallon", Unit("gallon", Dimension.VOLUME, 3.785411784))
        put("gal", Unit("gallon", Dimension.VOLUME, 3.785411784))
        put("fluidounce", Unit("floz", Dimension.VOLUME, 0.0295735))
        put("floz", Unit("floz", Dimension.VOLUME, 0.0295735))
    }
}
