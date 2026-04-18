package com.opensmarthome.speaker.ui.home

/**
 * Tri-state wrapper for data that the Home dashboard pulls from the
 * network. Surfacing `Loading` / `Error` explicitly is what closes the
 * "nothing appears on the tile and the user can't tell why" silent-
 * failure gap — the old `WeatherInfo? / List<NewsItem>` types could not
 * distinguish "still loading", "successfully empty", and "network
 * failure" so the UI simply showed blank in all three cases.
 *
 * Kept deliberately tiny: Loading/Success/Error only. Granular error
 * kinds let the cards pick appropriate copy (network vs. parse vs.
 * unknown) without leaking exception types into the UI layer.
 */
sealed interface BriefingState<out T> {
    /** First emission before any fetch has completed. UI should render a skeleton. */
    data object Loading : BriefingState<Nothing>

    /** Fetch succeeded. `data` may still be an empty list / null when that is meaningful. */
    data class Success<T>(val data: T) : BriefingState<T>

    /**
     * Fetch failed. UI picks human copy from [kind]; no exception details
     * are exposed to the dashboard layer so translation stays simple.
     */
    data class Error(val kind: Kind) : BriefingState<Nothing> {
        enum class Kind {
            /** IOException-family: offline, DNS failure, timeout. */
            Network,

            /** Parser failure (bad RSS, unexpected JSON shape). */
            Parse,

            /** Anything else we didn't categorize. */
            Unknown,
        }
    }
}
