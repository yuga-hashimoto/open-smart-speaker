package com.opensmarthome.speaker.tool.system

/**
 * Fuzzy app-name matcher used by [AndroidAppLauncher]. Pulled out of the
 * launcher so it stays unit-testable without Android classes.
 *
 * Scoring is ordinal, not absolute: the highest-scoring candidate wins as long
 * as it clears [MIN_SCORE]. Below the floor we return null so a mumbled
 * utterance doesn't launch a random app.
 *
 * Score components (all normalised to 0..100, picked by the `max`):
 * - 100: exact case-insensitive match
 *  -  90: exact match after stripping hint suffixes ("app", "アプリ")
 * -  80: all query tokens appear in the label, in any order (token-set)
 * -  60: label contains the (suffix-stripped) query as a substring
 * -  0-59: Levenshtein-normalised similarity between query and label
 */
internal object AppNameMatcher {

    const val MIN_SCORE = 60

    private val HINT_SUFFIXES = listOf(
        "アプリ", "app", "application", "を開いて", "を開く", "開いて", "ひらいて"
    )

    fun findBest(query: String, candidates: List<AppInfo>): AppInfo? {
        val cleaned = normalise(query)
        if (cleaned.isBlank() || candidates.isEmpty()) return null

        var best: AppInfo? = null
        var bestScore = 0
        for (candidate in candidates) {
            val score = score(cleaned, candidate.name)
            if (score > bestScore) {
                bestScore = score
                best = candidate
            }
        }
        return best?.takeIf { bestScore >= MIN_SCORE }
    }

    internal fun score(query: String, label: String): Int {
        val q = normalise(query)
        val l = normalise(label)
        if (q.isEmpty() || l.isEmpty()) return 0

        if (q == l) return 100

        val qStripped = stripHintSuffix(q)
        val lStripped = stripHintSuffix(l)
        if (qStripped.isNotEmpty() && qStripped == lStripped) return 90

        val tokens = qStripped.split(WHITESPACE).filter { it.isNotBlank() }
        if (tokens.isNotEmpty() && tokens.all { lStripped.contains(it) }) return 80

        if (qStripped.isNotEmpty() && lStripped.contains(qStripped)) return 60

        val sim = normalisedLevenshtein(qStripped, lStripped)
        return (sim * 59).toInt().coerceIn(0, 59)
    }

    private fun normalise(s: String): String =
        s.trim().lowercase()

    private fun stripHintSuffix(s: String): String {
        var current = s
        var changed = true
        while (changed) {
            changed = false
            for (suffix in HINT_SUFFIXES) {
                if (current.endsWith(suffix, ignoreCase = true) && current.length > suffix.length) {
                    current = current.dropLast(suffix.length).trim()
                    changed = true
                }
            }
        }
        return current
    }

    private fun normalisedLevenshtein(a: String, b: String): Double {
        val maxLen = maxOf(a.length, b.length)
        if (maxLen == 0) return 1.0
        val dist = levenshtein(a, b)
        return 1.0 - (dist.toDouble() / maxLen.toDouble())
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val prev = IntArray(b.length + 1) { it }
        val curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(
                    curr[j - 1] + 1,
                    prev[j] + 1,
                    prev[j - 1] + cost
                )
            }
            for (j in 0..b.length) prev[j] = curr[j]
        }
        return prev[b.length]
    }

    private val WHITESPACE = Regex("\\s+")
}
