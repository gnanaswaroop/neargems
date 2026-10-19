package com.drivescout.data

import com.drivescout.domain.LatLon
import com.drivescout.domain.Poi
import com.drivescout.domain.PoiCategory
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToLong

/** Landmarks from Wikipedia articles near the location, using the article intro as the snippet. */
class WikipediaSource(private val http: Http, private val language: String) {

    suspend fun fetch(center: LatLon, radiusMeters: Int): List<Poi> {
        val base = "https://$language.wikipedia.org/w/api.php"
        val coord = "${roundCoord(center.lat)}|${roundCoord(center.lon)}"
        val hits = parseGeoSearch(
            http.get("$base?action=query&list=geosearch&gscoord=$coord" +
                "&gsradius=${radiusMeters.coerceIn(10, 10_000)}&gslimit=30&format=json")
        )
        if (hits.isEmpty()) return emptyList()

        val intros = parseExtracts(
            http.get("$base?action=query&prop=extracts&exintro=1&explaintext=1&exsentences=2" +
                "&pageids=${hits.joinToString("|") { it.pageid.toString() }}&format=json")
        )
        return hits.mapNotNull { hit ->
            val intro = intros[hit.pageid]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            if (isAreaArticle(intro)) return@mapNotNull null
            Poi(
                id = "wiki:${hit.pageid}",
                name = hit.title,
                category = PoiCategory.LANDMARK,
                location = LatLon(hit.lat, hit.lon),
                snippet = trimSnippet(intro),
                interest = LANDMARK_INTEREST,
            )
        }
    }

    companion object {
        private const val LANDMARK_INTEREST = 40.0
        private const val MAX_SNIPPET_CHARS = 240
        private val json = Json { ignoreUnknownKeys = true }

        internal fun parseGeoSearch(body: String): List<GeoHit> =
            json.decodeFromString<GeoResponse>(body).query?.geosearch.orEmpty()

        internal fun parseExtracts(body: String): Map<Long, String?> =
            json.decodeFromString<ExtractResponse>(body).query?.pages?.values
                ?.associate { it.pageid to it.extract }
                .orEmpty()

        /**
         * Cities, towns, countries and regions come back from a nearby search too ("London is the
         * capital and largest city…"). They are areas, not places to visit, so they are skipped.
         */
        private val AREA_ARTICLE = Regex(
            """^[^.]*?\b(is|was) (the |an? )?([\w-]+ ){0,3}?(city|town|village|municipality|country|region|county|province|district|borough|capital)\b""",
            RegexOption.IGNORE_CASE,
        )

        internal fun isAreaArticle(intro: String): Boolean =
            AREA_ARTICLE.containsMatchIn(intro.substringBefore(". "))

        /** Collapses whitespace and cuts at a word boundary near [MAX_SNIPPET_CHARS]. */
        internal fun trimSnippet(text: String): String {
            val clean = text.replace(Regex("\\s+"), " ").trim()
            if (clean.length <= MAX_SNIPPET_CHARS) return clean
            val cut = clean.lastIndexOf(' ', MAX_SNIPPET_CHARS)
            return clean.substring(0, if (cut > 0) cut else MAX_SNIPPET_CHARS) + "…"
        }

        private fun roundCoord(value: Double): Double = (value * 1_000_000).roundToLong() / 1_000_000.0
    }
}

@Serializable
internal data class GeoResponse(val query: GeoQuery? = null)

@Serializable
internal data class GeoQuery(val geosearch: List<GeoHit> = emptyList())

@Serializable
internal data class GeoHit(val pageid: Long, val title: String, val lat: Double, val lon: Double)

@Serializable
internal data class ExtractResponse(val query: ExtractQuery? = null)

@Serializable
internal data class ExtractQuery(val pages: Map<String, ExtractPage> = emptyMap())

@Serializable
internal data class ExtractPage(val pageid: Long, val extract: String? = null)
