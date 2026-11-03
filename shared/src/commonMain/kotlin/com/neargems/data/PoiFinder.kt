package com.neargems.data

import com.neargems.domain.LatLon
import com.neargems.domain.Poi
import com.neargems.domain.PoiCategory
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull

class PoiFinder(
    private val overpass: OverpassSource,
    private val wikipedia: WikipediaSource,
    /** Longest a search may run. Tests use a shorter one. */
    private val searchBudgetMs: Long = SEARCH_BUDGET_MS,
) {

    /**
     * Gathers candidates from every source the [categories] need. One failing source does not
     * hide the other; only a total failure is thrown.
     *
     * [onPartial] is called with everything found so far each time a source answers, so the first
     * results can be shown without waiting for the slower source.
     *
     * The search always ends: after [SEARCH_BUDGET_MS] any source still waiting is abandoned, and the
     * places found so far are returned. A keyword search that did not finish is reported through [onWarning].
     */
    suspend fun find(
        center: LatLon,
        radiusMeters: Int,
        categories: Set<PoiCategory>,
        keywords: List<String> = emptyList(),
        onWarning: (String) -> Unit = {},
        onPartial: (List<Poi>) -> Unit = {},
    ): List<Poi> = supervisorScope {
        val answers = Channel<Result<List<Poi>>>(Channel.UNLIMITED)
        var keywordsAnswered = false
        val sources = listOf<Job>(
            launch { answers.send(runCatching { overpass.fetch(center, radiusMeters, categories) }) },
            launch {
                val named = runCatching { overpass.fetchNamed(center, radiusMeters, keywords) }
                // A cancelled search is not a failure to report: only a keyword search that really failed is.
                if (named.isFailure && keywords.isNotEmpty() && isActive) onWarning(KEYWORD_WARNING)
                keywordsAnswered = true
                answers.send(named)
            },
            launch {
                answers.send(
                    runCatching {
                        if (PoiCategory.LANDMARK in categories) wikipedia.fetch(center, radiusMeters) else emptyList()
                    },
                )
            },
        )
        val results = mutableListOf<Result<List<Poi>>>()
        val gathered = mutableListOf<Poi>()
        val finished = withTimeoutOrNull(searchBudgetMs) {
            repeat(SOURCES) {
                val answer = answers.receive()
                results += answer
                answer.getOrNull()?.let {
                    // A place can match a category and a keyword at once: keep it once.
                    gathered += it.filter { place -> gathered.none { seen -> seen.id == place.id } }
                    onPartial(gathered.toList())
                }
            }
        } != null
        if (!finished) {
            sources.forEach { it.cancel() }
            if (!keywordsAnswered && keywords.isNotEmpty()) onWarning(KEYWORD_WARNING)
        }
        if (results.isEmpty()) throw SearchTimedOut()
        if (results.all { it.isFailure }) throw results.first().exceptionOrNull()!!
        gathered
    }

    /** The search ran out of time before any source answered. */
    class SearchTimedOut : Exception("the search took too long")

    companion object {
        const val SOURCES = 3

        /** Longest a search may run before it ends with what it has. */
        const val SEARCH_BUDGET_MS = 30_000L

        /** Shown above the places when keyword places could not be fetched after retrying. */
        const val KEYWORD_WARNING = "Couldn't fetch places for your keywords right now. Showing the other places."
    }
}
