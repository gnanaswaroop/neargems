package com.neargems.data

import com.neargems.domain.LatLon
import com.neargems.domain.Poi
import com.neargems.domain.PoiCategory
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Food and drink places from OpenStreetMap through the public Overpass API. No key needed. */
class OverpassSource(private val http: Http) {

    suspend fun fetch(center: LatLon, radiusMeters: Int, categories: Set<PoiCategory>): List<Poi> {
        val amenities = categories.flatMap { it.osmAmenities }
        val shops = categories.flatMap { it.osmShops }
        if (amenities.isEmpty() && shops.isEmpty()) return emptyList()
        val filters = buildList {
            if (amenities.isNotEmpty()) {
                add("""nwr["amenity"~"^(${amenities.joinToString("|")})$"]["name"](around:$radiusMeters,${center.lat},${center.lon});""")
            }
            if (shops.isNotEmpty()) {
                add("""nwr["shop"~"^(${shops.joinToString("|")})$"]["name"](around:$radiusMeters,${center.lat},${center.lon});""")
            }
        }
        val query = """
            [out:json][timeout:15];
            (
              ${filters.joinToString("\n              ")}
            );
            out center 100;
        """.trimIndent()
        return withRetries { endpoint -> parse(http.postForm(endpoint, "data=" + query.encodeURLParameter())) }
    }

    /**
     * Named places whose name contains any of [keywords], such as "church" or "temple". These are found
     * whatever category they are in, so a keyword adds places next to the chosen categories.
     */
    suspend fun fetchNamed(center: LatLon, radiusMeters: Int, keywords: List<String>): List<Poi> {
        // A long list in one request is slow and often times out on the public server, so it goes in batches.
        return namedBatches(keywords).flatMap { batch ->
            withRetries { endpoint ->
                val pattern = batch.joinToString("|") { Regex.escape(it) }
                val query = """
                    [out:json][timeout:15];
                    nwr["name"~"($pattern)",i](around:$radiusMeters,${center.lat},${center.lon});
                    out center 100;
                """.trimIndent()
                parse(http.postForm(endpoint, "data=" + query.encodeURLParameter()), fallback = PoiCategory.LANDMARK)
            }
        }.distinctBy { it.id }
    }

    /**
     * Runs [block] against the Overpass servers in turn. When one is busy, rate-limited or times out, the next
     * one is tried after a short pause, so a single server going down does not stop the search.
     */
    private suspend fun <T> withRetries(block: suspend (endpoint: String) -> T): T {
        var pause = RETRY_PAUSE_MS
        repeat(ATTEMPTS - 1) { attempt ->
            try {
                return block(ENDPOINTS[attempt % ENDPOINTS.size])
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                delay(pause)
                pause *= 2
            }
        }
        return block(ENDPOINTS[(ATTEMPTS - 1) % ENDPOINTS.size])
    }

    companion object {
        /** The main Overpass server, then two public mirrors that run the same data. */
        private val ENDPOINTS = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
            "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
        )
        private const val ATTEMPTS = 3
        private const val RETRY_PAUSE_MS = 1_000L
        private const val NAMED_BATCH = 8

        /** Keywords grouped for separate requests, [NAMED_BATCH] at a time. */
        fun namedBatches(keywords: List<String>): List<List<String>> = keywords.chunked(NAMED_BATCH)
        private val json = Json { ignoreUnknownKeys = true }

        /** Turns an Overpass JSON response into places. */
        fun parse(body: String, fallback: PoiCategory? = null): List<Poi> =
            json.decodeFromString<OverpassResponse>(body).elements.mapNotNull { element ->
                val name = element.tags["name"] ?: return@mapNotNull null
                val lat = element.lat ?: element.center?.lat ?: return@mapNotNull null
                val lon = element.lon ?: element.center?.lon ?: return@mapNotNull null
                val category = categoryFor(element.tags["amenity"], element.tags["shop"]) ?: fallback ?: return@mapNotNull null
                Poi(
                    id = "osm:${element.type}:${element.id}",
                    name = name,
                    category = category,
                    location = LatLon(lat, lon),
                    snippet = snippetFor(element.tags, category),
                    interest = interestFor(element.tags),
                )
            }

        private fun categoryFor(amenity: String?, shop: String?): PoiCategory? =
            PoiCategory.entries.firstOrNull {
                (amenity != null && amenity in it.osmAmenities) || (shop != null && shop in it.osmShops)
            }

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
