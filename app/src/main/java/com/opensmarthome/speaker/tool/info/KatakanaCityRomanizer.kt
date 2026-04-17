package com.opensmarthome.speaker.tool.info

/**
 * Maps common katakana city names (Japanese loan-word spellings of foreign
 * places) to their canonical English form so the Open-Meteo geocoder can
 * find them.
 *
 * Open-Meteo's geocoding API is latin-only for non-Japanese places: it
 * returns zero hits for "シドニー" but resolves "Sydney" immediately. This
 * lookup covers the ~30 most common international cities a Japanese user
 * is likely to ask about. Entries are immutable and easy to extend.
 *
 * Behavior is defined only for whole-string katakana inputs. Partial or
 * mixed-script strings return null.
 */
object KatakanaCityRomanizer {

    /**
     * Canonical katakana → English city mapping. Sorted roughly by
     * global city size; add entries alphabetically within each region
     * when extending.
     */
    private val CITY_MAP: Map<String, String> = mapOf(
        // Oceania
        "シドニー" to "Sydney",
        "メルボルン" to "Melbourne",
        "オークランド" to "Auckland",

        // North America
        "ニューヨーク" to "New York",
        "ロサンゼルス" to "Los Angeles",
        "サンフランシスコ" to "San Francisco",
        "シカゴ" to "Chicago",
        "ホノルル" to "Honolulu",
        "トロント" to "Toronto",
        "バンクーバー" to "Vancouver",
        "シアトル" to "Seattle",
        "ボストン" to "Boston",
        "ワシントン" to "Washington",

        // Europe
        "ロンドン" to "London",
        "パリ" to "Paris",
        "ベルリン" to "Berlin",
        "ローマ" to "Rome",
        "マドリード" to "Madrid",
        "モスクワ" to "Moscow",
        "アムステルダム" to "Amsterdam",
        "ウィーン" to "Vienna",
        "ミュンヘン" to "Munich",
        "ストックホルム" to "Stockholm",

        // Asia
        "シンガポール" to "Singapore",
        "ソウル" to "Seoul",
        "バンコク" to "Bangkok",
        "台北" to "Taipei",
        "北京" to "Beijing",
        "上海" to "Shanghai",
        "香港" to "Hong Kong",
        "ジャカルタ" to "Jakarta",
        "マニラ" to "Manila",
        "ムンバイ" to "Mumbai",

        // Middle East / Africa
        "ドバイ" to "Dubai",
        "イスタンブール" to "Istanbul",
        "カイロ" to "Cairo"
    )

    /**
     * Returns the canonical English name for [katakana], or null if the
     * input isn't in the table. Matching is exact (no whitespace, case
     * normalization, or partial matches) because katakana city names are
     * unambiguous when they appear at all.
     */
    fun romanize(katakana: String): String? = CITY_MAP[katakana]
}
