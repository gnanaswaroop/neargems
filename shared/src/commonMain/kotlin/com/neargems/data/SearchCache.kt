package com.neargems.data

import com.neargems.domain.LatLon
import com.neargems.domain.Photo
import com.neargems.domain.Poi
import com.neargems.domain.PoiCategory
import com.neargems.platform.KeyValueStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The last search that finished, kept on the phone so the next open can show it without waiting. */
data class CachedSearch(
    val origin: LatLon,
    val categories: Set<PoiCategory>,
    val radiusMeters: Int,
    val keywords: List<String>,
    val places: List<Poi>,
)

/**
 * Saves the last completed search and loads it back when the app opens again. A saved search is only used for
 * [MAX_AGE_MS] after it was made; after that the places are searched for again.
 */
class SearchCache(private val store: KeyValueStore, private val clock: () -> Long) {

    fun save(search: CachedSearch) {
        val stored = StoredSearch(
            lat = search.origin.lat,
            lon = search.origin.lon,
            categories = search.categories.map { it.name },
            radiusMeters = search.radiusMeters,
            keywords = search.keywords,
            savedAt = clock(),
            places = search.places.map { it.toStored() },
        )
        store.putString(KEY, json.encodeToString(StoredSearch.serializer(), stored))
    }

    /** The saved search, or null when there is none, it is too old, or it cannot be read. */
    fun load(): CachedSearch? {
        val raw = store.getString(KEY) ?: return null
        val stored = try {
            json.decodeFromString(StoredSearch.serializer(), raw)
        } catch (e: Exception) {
            return null
        }
        if (clock() - stored.savedAt > MAX_AGE_MS) return null
        return CachedSearch(
            origin = LatLon(stored.lat, stored.lon),
            categories = stored.categories.mapNotNull { name -> PoiCategory.entries.firstOrNull { it.name == name } }.toSet(),
            radiusMeters = stored.radiusMeters,
            keywords = stored.keywords,
            places = stored.places.mapNotNull { it.toPoi() },
        )
    }

    @Serializable
    private data class StoredSearch(
        val lat: Double,
        val lon: Double,
        val categories: List<String>,
        val radiusMeters: Int,
        val keywords: List<String>,
        val savedAt: Long,
        val places: List<StoredPlace>,
    )

    @Serializable
    private data class StoredPlace(
        val id: String,
        val name: String,
        val category: String,
        val lat: Double,
        val lon: Double,
        val snippet: String,
        val interest: Double,
        val imageUrl: String? = null,
        val fileUrl: String? = null,
        val link: String? = null,
    )

    private fun Poi.toStored() = StoredPlace(
        id = id,
        name = name,
        category = category.name,
        lat = location.lat,
        lon = location.lon,
        snippet = snippet,
        interest = interest,
        imageUrl = photo?.imageUrl,
        fileUrl = photo?.fileUrl,
        link = link,
    )

    private fun StoredPlace.toPoi(): Poi? {
        val kind = PoiCategory.entries.firstOrNull { it.name == category } ?: return null
        return Poi(
            id = id,
            name = name,
            category = kind,
            location = LatLon(lat, lon),
            snippet = snippet,
            interest = interest,
            photo = imageUrl?.let { url -> Photo(url, fileUrl.orEmpty()) },
            link = link,
        )
    }

    companion object {
        private const val KEY = "last_search"
        /** A saved search older than this is searched for again. */
        const val MAX_AGE_MS = 12L * 60 * 60 * 1000
        private val json = Json { ignoreUnknownKeys = true }
    }
}
