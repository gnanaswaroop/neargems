package com.neargems.data

import com.neargems.domain.LatLon
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** A landmark keeps a link to its Wikipedia article, so "Read more on Wikipedia" can open it. */
class WikipediaLinkTest {

    private val geosearch = """{"query":{"geosearch":[
        {"pageid":123,"title":"Oxford Circus","lat":51.5152,"lon":-0.1415}
    ]}}"""

    private val extracts = """{"query":{"pages":{"123":{
        "pageid":123,
        "extract":"Oxford Circus is a major junction in London.",
        "pageimage":"Oxford Circus.jpg",
        "thumbnail":{"source":"https://upload.wikimedia.org/oxford.jpg"}
    }}}}"""

    @Test
    fun aLandmarkLinksToItsWikipediaArticle() = runBlocking {
        val client = HttpClient(
            MockEngine { request ->
                val body = if (request.url.parameters["list"] == "geosearch") geosearch else extracts
                respond(
                    content = body,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        )
        val source = WikipediaSource(Http(client), language = "en")

        val landmarks = source.fetch(LatLon(51.5152, -0.1415), radiusMeters = 1000)

        assertEquals(1, landmarks.size)
        assertEquals("https://en.wikipedia.org/?curid=123", landmarks.single().link)
    }
}
