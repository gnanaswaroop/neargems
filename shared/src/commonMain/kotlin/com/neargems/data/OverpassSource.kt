package com.neargems.data

import com.neargems.domain.LatLon
import com.neargems.domain.Poi
import com.neargems.domain.PoiCategory
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Food and drink places from OpenStreetMap through the public Overpass API. No key needed. */
class OverpassSource(private val http: Http) {

    suspend fun fetch(center: LatLon, radiusMeters: Int, categories: Set<PoiCategory>): List<Poi> {
        val amenities = categories.flatMap { it.osmAmenities }
        if (amenities.isEmpty()) return emptyList()
        val query = """
            [out:json][timeout:15];
            nwr["amenity"~"^(${amenities.joinToString("|")})$"]["name"](around:$radiusMeters,${center.lat},${center.lon});
            out center 100;
        """.trimIndent()
        return parse(http.postForm(ENDPOINT, "data=" + query.encodeURLParameter()))
    }

    /**
     * Named places whose name contains any of [keywords], such as "church" or "temple". These are found
     * whatever category they are in, so a keyword adds places next to the chosen categories.
     */
    suspend fun fetchNamed(center: LatLon, radiusMeters: Int, keywords: List<String>): List<Poi> {
        if (keywords.isEmpty()) return emptyList()
        val pattern = keywords.joinToString("|") { Regex.escape(it) }
        val query = """
            [out:json][timeout:15];
            nwr["name"~"($pattern)",i](around:$radiusMeters,${center.lat},${center.lon});
            out center 100;
        """.trimIndent()
        return parse(http.postForm(ENDPOINT, "data=" + query.encodeURLParameter()), fallback = PoiCategory.LANDMARK)
    }

    companion object {
        private const val ENDPOINT = "https://overpass-api.de/api/interpreter"
        private val json = Json { ignoreUnknownKeys = true }

        /** Turns an Overpass JSON response into places. */
        fun parse(body: String, fallback: PoiCategory? = null): List<Poi> =
            json.decodeFromString<OverpassResponse>(body).elements.mapNotNull { element ->
                val name = element.tags["name"] ?: return@mapNotNull null
                val lat = element.lat ?: element.center?.lat ?: return@mapNotNull null
                val lon = element.lon ?: element.center?.lon ?: return@mapNotNull null
                val category = categoryFor(element.tags["amenity"]) ?: fallback ?: return@mapNotNull null
                Poi(
                    id = "osm:${element.type}:${element.id}",
                    name = name,
                    category = category,
                    location = LatLon(lat, lon),
                    snippet = snippetFor(element.tags, category),
                    interest = interestFor(element.tags),
                )
            }

        private fun categoryFor(amenity: String?): PoiCategory? =
            PoiCategory.entries.firstOrNull { amenity != null && amenity in it.osmAmenities }

        private fun snippetFor(tags: Map<String, String>, category: PoiCategory): String {
            val cuisine = tags["cuisine"]
                ?.split(';')
                ?.joinToString(", ") { it.trim().replace('_', ' ').replaceFirstChar { c -> c.uppercase() } }
                ?.takeIf { it.isNotBlank() }
            val hours = tags["opening_hours"]?.let { "Hours: $it" }
            return listOfNotNull(cuisine, category.label, hours).joinToString(" · ")
        }

        /** Places with more information (cuisine, website, hours, phone) rank a little higher. */
        private fun interestFor(tags: Map<String, String>): Double {
            var score = 10.0
            if ("cuisine" in tags) score += 5
            if ("website" in tags || "contact:website" in tags) score += 5
            if ("opening_hours" in tags) score += 3
            if ("phone" in tags) score += 2
            return score
        }
    }
}

@Serializable
data class OverpassResponse(val elements: List<OverpassElement> = emptyList())

@Serializable
data class OverpassElement(
    val type: String,
    val id: Long,
    val lat: Double? = null,
    val lon: Double? = null,
    val center: OverpassCenter? = null,
    val tags: Map<String, String> = emptyMap(),
)

@Serializable
data class OverpassCenter(val lat: Double, val lon: Double)
