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
        onPartial: (List<Poi>) -> Unit = {},
    ): List<Poi> = supervisorScope {
        val answers = Channel<Result<List<Poi>>>(Channel.UNLIMITED)
        launch { answers.send(runCatching { overpass.fetch(center, radiusMeters, categories) }) }
        launch { answers.send(runCatching { overpass.fetchNamed(center, radiusMeters, keywords) }) }
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

    private companion object {
        const val SOURCES = 3
    }
}
