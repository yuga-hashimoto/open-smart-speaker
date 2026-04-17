package com.opensmarthome.speaker.ui.home

import java.util.Locale

/**
 * Time-of-day greeting ("Good morning", "おはようございます") for the Home
 * dashboard. Pure function so tests can pin the hour and locale without
 * touching `Clock.systemDefaultZone()`.
 *
 * Greetings are shipped as in-code constants rather than `strings.xml`
 * entries to keep this cycle free of the 30-locale parity-resource
 * churn. The trade-off is limited locale coverage for now — en/ja
 * cover the primary user languages, other locales fall back to
 * English. If/when the feature catches on, migrate to strings.xml
 * alongside the rest of the `LocaleStringsParityTest`-governed keys.
 */
object GreetingResolver {

    enum class Bucket {
        MORNING,      // 05:00 – 10:59
        AFTERNOON,    // 11:00 – 16:59
        EVENING,      // 17:00 – 21:59
        NIGHT,        // 22:00 – 04:59
    }

    /** Map an hour-of-day (0..23) to a greeting bucket. */
    fun bucketForHour(hour: Int): Bucket {
        val h = hour.coerceIn(0, 23)
        return when (h) {
            in 5..10 -> Bucket.MORNING
            in 11..16 -> Bucket.AFTERNOON
            in 17..21 -> Bucket.EVENING
            else -> Bucket.NIGHT
        }
    }

    /**
     * Greeting string for the given hour and locale. Falls back to
     * English for any locale we don't explicitly translate.
     */
    fun greetingFor(hour: Int, locale: Locale = Locale.getDefault()): String {
        val bucket = bucketForHour(hour)
        return when (locale.language) {
            "ja" -> when (bucket) {
                Bucket.MORNING -> "おはようございます"
                Bucket.AFTERNOON -> "こんにちは"
                Bucket.EVENING -> "こんばんは"
                Bucket.NIGHT -> "お疲れさまでした"
            }
            else -> when (bucket) {
                Bucket.MORNING -> "Good morning"
                Bucket.AFTERNOON -> "Good afternoon"
                Bucket.EVENING -> "Good evening"
                Bucket.NIGHT -> "Good night"
            }
        }
    }
}
