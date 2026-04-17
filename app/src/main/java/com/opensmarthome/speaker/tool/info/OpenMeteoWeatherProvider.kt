package com.opensmarthome.speaker.tool.info

import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Weather provider using Open-Meteo API (free, no auth required).
 * Docs: https://open-meteo.com/en/docs
 *
 * [geoApiUrl] and [forecastApiUrl] are overridable for testing (MockWebServer).
 */
class OpenMeteoWeatherProvider(
    private val client: OkHttpClient,
    private val moshi: Moshi,
    private val geoApiUrl: String = DEFAULT_GEO_API,
    private val forecastApiUrl: String = DEFAULT_FORECAST_API
) : WeatherProvider {

    companion object {
        const val DEFAULT_GEO_API = "https://geocoding-api.open-meteo.com/v1/search"
        const val DEFAULT_FORECAST_API = "https://api.open-meteo.com/v1/forecast"

        /**
         * Japanese administrative suffixes the Open-Meteo geocoder often
         * fails to match. "宗像市" returns zero results, "宗像" returns one.
         * When the initial search returns zero results we strip one of these
         * and retry under `language=ja`.
         */
        internal val JAPANESE_SUFFIXES: List<String> = listOf(
            "市", "町", "区", "村", "府", "県"
        )

        /**
         * Matches a leading run of CJK Unified Ideographs (kanji). Used as a
         * last-ditch stem after suffix stripping also returned zero results
         * (e.g. "東京都新宿区" → "東京").
         */
        internal val LEADING_KANJI_REGEX = Regex("^[\\p{IsHan}]+")
    }

    override suspend fun getCurrent(location: String?): WeatherInfo = withContext(Dispatchers.IO) {
        val coords = resolveCoordinates(location)
        val url = forecastApiUrl.toHttpUrl().newBuilder().apply {
            addQueryParameter("latitude", coords.latitude.toString())
            addQueryParameter("longitude", coords.longitude.toString())
            addQueryParameter("current", "temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m")
            addQueryParameter("timezone", "auto")
        }.build()

        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("Weather API error: ${response.code}")
            }
            val body = response.body?.string() ?: throw RuntimeException("Empty response")
            parseCurrentWeather(body, coords.name)
        }
    }

    override suspend fun getForecast(location: String?, days: Int): List<DayForecast> = withContext(Dispatchers.IO) {
        val coords = resolveCoordinates(location)
        val url = forecastApiUrl.toHttpUrl().newBuilder().apply {
            addQueryParameter("latitude", coords.latitude.toString())
            addQueryParameter("longitude", coords.longitude.toString())
            addQueryParameter("daily", "temperature_2m_max,temperature_2m_min,weather_code")
            addQueryParameter("forecast_days", days.toString())
            addQueryParameter("timezone", "auto")
        }.build()

        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("Forecast API error: ${response.code}")
            }
            val body = response.body?.string() ?: throw RuntimeException("Empty response")
            parseForecast(body)
        }
    }

    internal data class Coords(val latitude: Double, val longitude: Double, val name: String)

    /**
     * Resolve a location string to coordinates with up to three attempts to
     * work around Open-Meteo's patchy matching of Japanese administrative
     * suffixes ("市", "町", "区", "村", "府", "県"):
     *
     * 1. Original query, `language=en` (fast path — works for English and
     *    well-known romanized city names).
     * 2. If zero results and query contains a Japanese suffix, strip a single
     *    suffix and retry with `language=ja` (e.g. "宗像市" → "宗像").
     * 3. If still zero results and query contains leading kanji, reduce to
     *    the leading kanji run and retry with `language=ja`
     *    (e.g. "東京都新宿区" → "東京").
     * 4. Otherwise throw RuntimeException as before.
     */
    internal suspend fun resolveCoordinates(location: String?): Coords = withContext(Dispatchers.IO) {
        // If no location provided, default to Tokyo (could be enhanced with device location)
        val query = location?.takeIf { it.isNotBlank() } ?: "Tokyo"

        geocodeOnce(query, language = "en")
            ?: stripJapaneseSuffix(query)?.let { geocodeOnce(it, language = "ja") }
            ?: leadingKanjiStem(query)?.let { geocodeOnce(it, language = "ja") }
            ?: throw RuntimeException("No geocoding results for $query")
    }

    private fun geocodeOnce(query: String, language: String): Coords? {
        val url = geoApiUrl.toHttpUrl().newBuilder().apply {
            addQueryParameter("name", query)
            addQueryParameter("count", "1")
            addQueryParameter("language", language)
        }.build()
        val request = Request.Builder().url(url).get().build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("Geocoding error: ${response.code}")
            }
            val body = response.body?.string() ?: throw RuntimeException("Empty geocoding response")
            parseGeocoding(body, query)
        }
    }

    /**
     * Strip a trailing Japanese administrative suffix. Returns `null` if the
     * query doesn't end in one of [JAPANESE_SUFFIXES] or if stripping would
     * leave an empty string.
     */
    internal fun stripJapaneseSuffix(query: String): String? {
        for (suffix in JAPANESE_SUFFIXES) {
            if (query.endsWith(suffix) && query.length > suffix.length) {
                return query.substring(0, query.length - suffix.length)
            }
        }
        return null
    }

    /**
     * Extract the leading kanji run. Returns `null` if the query has no
     * leading kanji, or if the stem equals the full query (nothing to reduce).
     */
    internal fun leadingKanjiStem(query: String): String? {
        val match = LEADING_KANJI_REGEX.find(query)?.value ?: return null
        return match.takeIf { it.isNotEmpty() && it != query }
    }

    /**
     * Returns `null` when the response is shaped correctly but contains zero
     * results — the caller uses `null` as the signal to try a fallback
     * strategy (suffix strip, leading-kanji stem). Throws only on malformed
     * responses where retry cannot help.
     */
    @Suppress("UNCHECKED_CAST")
    internal fun parseGeocoding(json: String, fallbackName: String): Coords? {
        val root = moshi.adapter(Map::class.java).fromJson(json) as? Map<String, Any?>
            ?: throw RuntimeException("Invalid geocoding response")
        val results = root["results"] as? List<Map<String, Any?>> ?: return null
        val first = results.firstOrNull() ?: return null
        return Coords(
            latitude = (first["latitude"] as Number).toDouble(),
            longitude = (first["longitude"] as Number).toDouble(),
            name = first["name"] as? String ?: fallbackName
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseCurrentWeather(json: String, locationName: String): WeatherInfo {
        val root = moshi.adapter(Map::class.java).fromJson(json) as? Map<String, Any?>
            ?: throw RuntimeException("Invalid weather response")
        val current = root["current"] as? Map<String, Any?>
            ?: throw RuntimeException("No current weather data")

        val temp = (current["temperature_2m"] as Number).toDouble()
        val humidity = (current["relative_humidity_2m"] as Number).toInt()
        val wind = (current["wind_speed_10m"] as Number).toDouble()
        val code = (current["weather_code"] as Number).toInt()

        return WeatherInfo(
            location = locationName,
            temperatureC = temp,
            condition = weatherCodeToText(code),
            humidity = humidity,
            windKph = wind
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseForecast(json: String): List<DayForecast> {
        val root = moshi.adapter(Map::class.java).fromJson(json) as? Map<String, Any?>
            ?: throw RuntimeException("Invalid forecast response")
        val daily = root["daily"] as? Map<String, Any?>
            ?: throw RuntimeException("No daily forecast")

        val times = daily["time"] as List<String>
        val mins = daily["temperature_2m_min"] as List<Number>
        val maxs = daily["temperature_2m_max"] as List<Number>
        val codes = daily["weather_code"] as List<Number>

        return times.indices.map { i ->
            DayForecast(
                date = times[i],
                minTempC = mins[i].toDouble(),
                maxTempC = maxs[i].toDouble(),
                condition = weatherCodeToText(codes[i].toInt())
            )
        }
    }

    private fun weatherCodeToText(code: Int): String = when (code) {
        0 -> "Clear"
        1, 2, 3 -> "Partly cloudy"
        45, 48 -> "Fog"
        51, 53, 55 -> "Drizzle"
        61, 63, 65 -> "Rain"
        71, 73, 75 -> "Snow"
        77 -> "Snow grains"
        80, 81, 82 -> "Rain showers"
        85, 86 -> "Snow showers"
        95 -> "Thunderstorm"
        96, 99 -> "Thunderstorm with hail"
        else -> "Unknown"
    }
}
