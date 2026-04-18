package com.opensmarthome.speaker.data.location

import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Single Open-Meteo geocoding result as surfaced to the Settings picker.
 * The raw geocoding payload also contains fields the picker does not need
 * (timezone, population, feature_code, etc.) — we intentionally keep this
 * model small to avoid leaking provider-specific shape into the UI layer.
 *
 * [displayLabel] is pre-computed at the repository layer so the picker
 * never needs to worry about joining fragments; `"宗像, Fukuoka, Japan"`
 * is what the UI renders verbatim and what [PreferenceKeys.DEFAULT_LOCATION]
 * persists when the user selects this suggestion.
 */
data class CitySuggestion(
    val name: String,
    val admin1: String?,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    val displayLabel: String
)

/**
 * Returns candidate cities matching a free-text query. Used by the
 * Weather location picker in Settings. Implementations are expected to
 * swallow provider-specific errors and return `Result.failure` so the
 * UI can render a friendly "no results" state without crashing.
 *
 * Stolen from dicio-android's weather skill (free-text city + auto
 * fallback) and home-assistant/android's EntityPicker (debounced
 * search + candidate list).
 */
interface CitySearchRepository {
    suspend fun search(query: String, language: String = "en"): Result<List<CitySuggestion>>
}

/**
 * Default implementation backed by Open-Meteo's Geocoding API
 * (`https://geocoding-api.open-meteo.com/v1/search`). This is the same
 * endpoint [com.opensmarthome.speaker.tool.info.OpenMeteoWeatherProvider]
 * uses for its forecast lookups — keeping both callers on one provider
 * avoids a second External Service Review and preserves rate-limit
 * parity.
 *
 * @param apiUrl overridable for MockWebServer tests.
 */
class OpenMeteoCitySearchRepository(
    private val client: OkHttpClient,
    private val moshi: Moshi,
    private val apiUrl: String = DEFAULT_API_URL
) : CitySearchRepository {

    companion object {
        const val DEFAULT_API_URL = "https://geocoding-api.open-meteo.com/v1/search"

        /**
         * Open-Meteo caps `count` at 100; 10 is plenty for a dropdown and
         * keeps the response payload small (important on tethered / slow
         * connections where the picker is most valuable).
         */
        internal const val DEFAULT_COUNT = 10
    }

    override suspend fun search(query: String, language: String): Result<List<CitySuggestion>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return Result.success(emptyList())
        }
        return runCatching {
            withContext(Dispatchers.IO) {
                val url = apiUrl.toHttpUrl().newBuilder().apply {
                    addQueryParameter("name", trimmed)
                    addQueryParameter("count", DEFAULT_COUNT.toString())
                    addQueryParameter("language", language)
                }.build()
                val request = Request.Builder().url(url).get().build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw RuntimeException("Geocoding error: ${response.code}")
                    }
                    val body = response.body?.string()
                        ?: throw RuntimeException("Empty geocoding response")
                    parse(body)
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    internal fun parse(json: String): List<CitySuggestion> {
        val root = moshi.adapter(Map::class.java).fromJson(json) as? Map<String, Any?>
            ?: return emptyList()
        val results = root["results"] as? List<Map<String, Any?>> ?: return emptyList()
        return results.mapNotNull { raw ->
            val name = raw["name"] as? String ?: return@mapNotNull null
            val latitude = (raw["latitude"] as? Number)?.toDouble() ?: return@mapNotNull null
            val longitude = (raw["longitude"] as? Number)?.toDouble() ?: return@mapNotNull null
            val country = raw["country"] as? String ?: ""
            val admin1 = raw["admin1"] as? String
            CitySuggestion(
                name = name,
                admin1 = admin1?.takeIf { it.isNotBlank() },
                country = country,
                latitude = latitude,
                longitude = longitude,
                displayLabel = listOfNotNull(
                    name,
                    admin1?.takeIf { it.isNotBlank() },
                    country.takeIf { it.isNotBlank() }
                ).joinToString(", ")
            )
        }
    }
}
