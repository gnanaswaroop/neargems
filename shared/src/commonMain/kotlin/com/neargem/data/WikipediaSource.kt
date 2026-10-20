package com.neargem.data

import com.neargem.domain.LatLon
import com.neargem.domain.Poi
import com.neargem.domain.Photo
import com.neargem.domain.PoiCategory
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

        val pages = parseExtracts(
            http.get("$base?action=query&prop=extracts|pageimages&exintro=1&explaintext=1&exsentences=2" +
                "&piprop=thumbnail|name&pithumbsize=$THUMBNAIL_WIDTH_PX" +
                "&pageids=${hits.joinToString("|") { it.pageid.toString() }}&format=json")
        )
        return hits.mapNotNull { hit ->
            val page = pages[hit.pageid] ?: return@mapNotNull null
            val intro = page.extract?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            if (isAreaArticle(intro)) return@mapNotNull null
            Poi(
                id = "wiki:${hit.pageid}",
                name = hit.title,
                category = PoiCategory.LANDMARK,
                location = LatLon(hit.lat, hit.lon),
                snippet = trimSnippet(intro),
                interest = LANDMARK_INTEREST,
                photo = photoFor(page, language),
                link = "https://$language.wikipedia.org/?curid=${hit.pageid}",
            )
        }
    }

    companion object {
        private const val LANDMARK_INTEREST = 40.0
        private const val MAX_SNIPPET_CHARS = 240
        private val json = Json { ignoreUnknownKeys = true }

        fun parseGeoSearch(body: String): List<GeoHit> =
            json.decodeFromString<GeoResponse>(body).query?.geosearch.orEmpty()

        fun parseExtracts(body: String): Map<Long, ExtractPage> =
            json.decodeFromString<ExtractResponse>(body).query?.pages?.values
                ?.associateBy { it.pageid }
                .orEmpty()

        /** The article's lead image as a URL to load at runtime, with the file page as its credit link. */
        fun photoFor(page: ExtractPage, language: String): Photo? {
            val thumbnail = page.thumbnail?.source ?: return null
            val file = page.pageimage ?: return null
            return Photo(
                imageUrl = thumbnail,
                fileUrl = "https://$language.wikipedia.org/wiki/File:${file.replace(' ', '_')}",
            )
        }

        /**
         * Cities, towns, countries and regions come back from a nearby search too ("London is the
         * capital and largest city…"). They are areas, not places to visit, so they are skipped.
         */
        private val AREA_ARTICLE = Regex(
            """^[^.]*?\b(is|was) (the |an? )?([\w-]+ ){0,3}?(city|town|village|municipality|country|region|county|province|district|borough|capital)\b""",
            RegexOption.IGNORE_CASE,
        )

        fun isAreaArticle(intro: String): Boolean =
            AREA_ARTICLE.containsMatchIn(intro.substringBefore(". "))

        /** Collapses whitespace and cuts at a word boundary near [MAX_SNIPPET_CHARS]. */
        fun trimSnippet(text: String): String {
            val clean = text.replace(Regex("\\s+"), " ").trim()
            if (clean.length <= MAX_SNIPPET_CHARS) return clean
            val cut = clean.lastIndexOf(' ', MAX_SNIPPET_CHARS)
            return clean.substring(0, if (cut > 0) cut else MAX_SNIPPET_CHARS) + "…"
        }

        private fun roundCoord(value: Double): Double = (value * 1_000_000).roundToLong() / 1_000_000.0
    }
}

@Serializable
data class GeoResponse(val query: GeoQuery? = null)

@Serializable
data class GeoQuery(val geosearch: List<GeoHit> = emptyList())

@Serializable
data class GeoHit(val pageid: Long, val title: String, val lat: Double, val lon: Double)

@Serializable
data class ExtractResponse(val query: ExtractQuery? = null)

@Serializable
data class ExtractQuery(val pages: Map<String, ExtractPage> = emptyMap())

@Serializable
data class ExtractPage(
    val pageid: Long,
    val extract: String? = null,
    val thumbnail: Thumbnail? = null,
    val pageimage: String? = null,
)

@Serializable
data class Thumbnail(val source: String)

private const val THUMBNAIL_WIDTH_PX = 640
