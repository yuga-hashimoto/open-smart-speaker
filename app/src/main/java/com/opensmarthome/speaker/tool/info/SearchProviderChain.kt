package com.opensmarthome.speaker.tool.info

/**
 * Delegates to a list of [SearchProvider]s in order. Returns the first
 * non-empty result (non-blank abstract or non-empty relatedTopics). If all
 * providers return empty, returns the first provider's empty result. If all
 * providers throw, rethrows the first exception.
 *
 * This is a pure [SearchProvider]→[SearchProvider] chain, distinct from
 * [ChainedSearchProvider] which wraps a [LanguageAwareSearchProvider] fallback
 * (Wikipedia). Use this chain when stacking plain [SearchProvider]s (e.g.
 * DuckDuckGo HTML scrape → Instant Answer API).
 */
class SearchProviderChain(
    private val providers: List<SearchProvider>
) : SearchProvider {

    init {
        require(providers.isNotEmpty()) { "SearchProviderChain requires at least one provider" }
    }

    override suspend fun search(query: String): SearchResult {
        var firstEmpty: SearchResult? = null
        var firstError: Throwable? = null
        for (provider in providers) {
            val attempt = runCatching { provider.search(query) }
            attempt.fold(
                onSuccess = { result ->
                    if (result.abstract.isNotBlank() || result.relatedTopics.isNotEmpty()) {
                        return result
                    }
                    if (firstEmpty == null) firstEmpty = result
                },
                onFailure = { e ->
                    if (firstError == null) firstError = e
                }
            )
        }
        firstEmpty?.let { return it }
        throw firstError ?: RuntimeException("SearchProviderChain: all providers failed")
    }
}
