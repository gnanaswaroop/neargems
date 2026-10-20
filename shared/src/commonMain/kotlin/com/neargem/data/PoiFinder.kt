package com.neargem.data

import com.neargem.domain.LatLon
import com.neargem.domain.Poi
import com.neargem.domain.PoiCategory
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope

class PoiFinder(private val overpass: OverpassSource, private val wikipedia: WikipediaSource) {

    /**
     * Gathers candidates from every source the [categories] need. One failing source does not
     * hide the other; only a total failure is thrown.
     */
    suspend fun find(center: LatLon, radiusMeters: Int, categories: Set<PoiCategory>): List<Poi> =
        supervisorScope {
            val food = async { runCatching { overpass.fetch(center, radiusMeters, categories) } }
            val landmarks = async {
                runCatching {
                    if (PoiCategory.LANDMARK in categories) wikipedia.fetch(center, radiusMeters) else emptyList()
                }
            }
            val results = listOf(food.await(), landmarks.await())
            if (results.all { it.isFailure }) throw results.first().exceptionOrNull()!!
            results.flatMap { it.getOrDefault(emptyList()) }
        }
}
