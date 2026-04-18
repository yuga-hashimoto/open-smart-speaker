package com.opendash.app.tool.info

/**
 * Two-stage SearchProvider: delegates to [primary] first; if the primary
 * returns an empty result (no abstract and no related topics) or throws,
 * falls back to [fallback] with language retry (ja then en).
 *
 * Rationale: the DuckDuckGo Instant Answer API commonly returns 202-style
 * empty payloads for general web queries (it is designed for instant answers
 * like math, unit conversions, and well-known topics, not general search).
 * Wikipedia's public REST API fills that gap with encyclopedic summaries.
 */
class ChainedSearchProvider(
    private val primary: SearchProvider,
    private val fallback: LanguageAwareSearchProvider,
    private val fallbackLangs: List<String> = listOf("ja", "en")
) : SearchProvider {

    override suspend fun search(query: String): SearchResult {
        val primaryResult = runCatching { primary.search(query) }
        primaryResult.getOrNull()?.let { result ->
            if (result.abstract.isNotBlank() || result.relatedTopics.isNotEmpty()) {
                return result
            }
        }

        for (lang in fallbackLangs) {
            val fallbackResult = runCatching { fallback.search(query, lang) }.getOrNull()
            if (fallbackResult != null && fallbackResult.abstract.isNotBlank()) {
                return fallbackResult
            }
        }

        // Primary returned empty (or threw). Prefer primary's empty result; if
        // primary threw, rethrow so the caller surfaces the network error.
        return primaryResult.getOrElse { throw it }
    }
}
