package com.neargems.data

import com.neargems.domain.LatLon
import com.neargems.domain.PoiCategory
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the keyword search cannot be reached, the finder says so and still returns the other places. */
class KeywordWarningTest {

    private val center = LatLon(51.5080, -0.1281)

    @Test
    fun aFailedKeywordSearchIsReportedAndTheOtherPlacesStillShow() {
        val client = HttpClient(
            MockEngine { request ->
                if (request.url.host in setOf("overpass-api.de", "overpass.kumi.systems", "maps.mail.ru")) {
                    respond(content = "busy", status = HttpStatusCode.BadGateway, headers = headersOf(HttpHeaders.ContentType, "text/plain"))
                } else {
                    respond(
                        content = """{"query":{"geosearch":[]}}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            },
        )
        val http = Http(client)
        val finder = PoiFinder(OverpassSource(http), WikipediaSource(http, language = "en"))

        val warnings = mutableListOf<String>()
        runBlocking {
            finder.find(
                center = center,
                radiusMeters = 1000,
                categories = setOf(PoiCategory.LANDMARK),
                keywords = listOf("church"),
                onWarning = { warnings += it },
            )
        }

        assertEquals(listOf(PoiFinder.KEYWORD_WARNING), warnings)
        assertTrue(warnings.single().contains("keywords"))
    }
}
