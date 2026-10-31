package com.neargems.data

import com.neargems.domain.LatLon
import com.neargems.domain.Poi
import com.neargems.domain.PoiCategory
import com.neargems.platform.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A place the user marked as visited. Kept on this device only, so it can be opened or shown again. */
data class VisitedPlace(
    val id: String,
    val name: String,
    val category: PoiCategory,
    val location: LatLon,
    val atMillis: Long,
)

/** The places marked as visited, newest first. Stored in one key-value entry, one place per line. */
class VisitedHistory(private val store: KeyValueStore) {
    private val _places = MutableStateFlow(read())
    val places: StateFlow<List<VisitedPlace>> = _places.asStateFlow()

    fun add(poi: Poi, atMillis: Long) {
        val next = listOf(VisitedPlace(poi.id, clean(poi.name), poi.category, poi.location, atMillis)) +
            _places.value.filter { it.id != poi.id }
        write(next)
        _places.value = next
    }

    fun remove(id: String) {
        val next = _places.value.filter { it.id != id }
        write(next)
        _places.value = next
    }

    private fun write(list: List<VisitedPlace>) {
        store.putString(
            KEY,
            list.joinToString("\n") {
                listOf(it.id, it.name, it.category.name, it.location.lat, it.location.lon, it.atMillis).joinToString("|")
            },
        )
    }

    private fun read(): List<VisitedPlace> =
        store.getString(KEY).orEmpty().lines().mapNotNull { line ->
            val parts = line.split('|')
            if (parts.size != 6) return@mapNotNull null
            val category = PoiCategory.entries.firstOrNull { it.name == parts[2] } ?: return@mapNotNull null
            val lat = parts[3].toDoubleOrNull() ?: return@mapNotNull null
            val lon = parts[4].toDoubleOrNull() ?: return@mapNotNull null
            val at = parts[5].toLongOrNull() ?: return@mapNotNull null
            VisitedPlace(parts[0], parts[1], category, LatLon(lat, lon), at)
        }.sortedByDescending { it.atMillis }

    /** The separator and line breaks are not allowed in names, so they are replaced. */
    private fun clean(text: String): String = text.replace('|', ' ').replace('\n', ' ')

    private companion object {
        const val KEY = "visited_places"
    }
}

/**
 * Whether a visited place is still worth showing: it is within [radiusMeters] of [origin], and it is in a
 * chosen category or its name contains a chosen keyword. The same rule the suggestions use.
 */
fun VisitedPlace.matches(
    origin: LatLon,
    radiusMeters: Int,
    categories: Set<PoiCategory>,
    keywords: List<String>,
): Boolean {
    if (com.neargems.domain.distanceMeters(origin, location) > radiusMeters) return false
    val byName = keywords.any { it in name.lowercase() }
    return category in categories || byName
}
