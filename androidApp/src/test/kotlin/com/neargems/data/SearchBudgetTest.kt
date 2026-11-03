package com.neargems.data

import com.neargems.domain.LatLon
import com.neargems.domain.PoiCategory
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A search with keywords must end, even when a keyword request never answers, so the refresh indicator stops. */
class SearchBudgetTest {

    private val center = LatLon(51.5080, -0.1281)

    @Test
    fun aKeywordSearchThatNeverAnswersEndsWithTheOtherPlacesAndAWarning() = runBlocking {
        val client = HttpClient(
            MockEngine { request ->
                val query = (request.body as TextContent).text
                if (query.contains("%22name%22~")) {
                    // A keyword request that never comes back.
                    awaitCancellation()
                }
                respond(
                    content = """{"elements":[{"type":"node","id":7,"lat":51.5081,"lon":-0.1282,"tags":{"name":"Hobbs","shop":"bakery"}}]}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        )
        val http = Http(client)
        val finder = PoiFinder(OverpassSource(http), WikipediaSource(http, language = "en"), searchBudgetMs = 2_000)
        val warnings = mutableListOf<String>()

        val started = System.currentTimeMillis()
        val found = finder.find(
            center = center,
            radiusMeters = 1000,
            categories = setOf(PoiCategory.BAKERY),
            keywords = listOf("church"),
            onWarning = { warnings += it },
        )

        assertEquals(listOf("Hobbs"), found.map { it.name })
        assertEquals(listOf(PoiFinder.KEYWORD_WARNING), warnings)
        assertTrue("the search should end within its budget", System.currentTimeMillis() - started < 10_000)
    }

    @Test
    fun keywordsAreCleanedSoTheQueryStaysValid() {
        val query = OverpassSource.namedQuery(center, 1000, listOf("st. \"james\"", "(x)", "pub-crawl"))

        assertTrue(query, query.contains("""nwr["name"~"(st james|x|pub-crawl)",i]"""))
        assertEquals(false, query.contains("\\"))
    }
}
