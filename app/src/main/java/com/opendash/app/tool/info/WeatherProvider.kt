package com.opendash.app.tool.info

/**
 * Fetches weather information.
 * Implementation uses Open-Meteo API (free, no auth required).
 */
interface WeatherProvider {
    suspend fun getCurrent(location: String?): WeatherInfo
    suspend fun getForecast(location: String?, days: Int): List<DayForecast>
}

data class WeatherInfo(
    val location: String,
    val temperatureC: Double,
    val condition: String,
    val humidity: Int,
    val windKph: Double
)

data class DayForecast(
    val date: String,
    val minTempC: Double,
    val maxTempC: Double,
    val condition: String
)
