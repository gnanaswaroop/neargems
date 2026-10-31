package com.neargems.data

import com.neargems.domain.LatLon
import com.neargems.domain.Poi
import com.neargems.domain.PoiCategory
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

class PoiFinder(private val overpass: OverpassSource, private val wikipedia: WikipediaSource) {

    /**
     * Gathers candidates from every source the [categories] need. One failing source does not
     * hide the other; only a total failure is thrown.
     *
     * [onPartial] is called with everything found so far each time a source answers, so the first
     * results can be shown without waiting for the slower source.
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
        launch { answers.send(runCatching { overpass.fetch(center, radiusMeters, categories) }) }
        launch {
            val named = runCatching { overpass.fetchNamed(center, radiusMeters, keywords) }
            if (named.isFailure && keywords.isNotEmpty()) onWarning(KEYWORD_WARNING)
            answers.send(named)
        }
        launch {
            answers.send(
                runCatching {
                    if (PoiCategory.LANDMARK in categories) wikipedia.fetch(center, radiusMeters) else emptyList()
                },
            )
        }
        val results = mutableListOf<Result<List<Poi>>>()
        val gathered = mutableListOf<Poi>()
        repeat(SOURCES) {
            val answer = answers.receive()
            results += answer
            answer.getOrNull()?.let {
                // A place can match a category and a keyword at once: keep it once.
                gathered += it.filter { place -> gathered.none { seen -> seen.id == place.id } }
                onPartial(gathered.toList())
            }
        }
        if (results.all { it.isFailure }) throw results.first().exceptionOrNull()!!
        gathered
    }

    companion object {
        const val SOURCES = 3

        /** Shown above the places when keyword places could not be fetched after retrying. */
        const val KEYWORD_WARNING = "Couldn't fetch places for your keywords right now. Showing the other places."
    }
}
