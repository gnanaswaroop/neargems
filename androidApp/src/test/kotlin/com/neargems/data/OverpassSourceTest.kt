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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverpassSourceTest {

    @Test
    fun aBakeryIsAShopAndIsAskedForByItsShopTag() = runBlocking {
        val queries = mutableListOf<String>()
        val client = HttpClient(
            MockEngine { request ->
                queries += (request.body as TextContent).text
                respond(
                    content = """{"elements":[{"type":"node","id":4,"lat":51.5,"lon":-0.1,"tags":{"name":"Hobbs","shop":"bakery"}}]}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        )

        val pois = OverpassSource(Http(client)).fetch(LatLon(51.5, -0.1), 1000, setOf(PoiCategory.BAKERY))

        assertEquals(PoiCategory.BAKERY, pois.single().category)
        assertEquals("Hobbs", pois.single().name)
        // Only the shop filter is sent for a bakery, not the amenity filter.
        assertTrue(queries.single().contains("shop"))
        assertFalse(queries.single().contains("amenity"))
    }

    @Test
    fun bakeriesAreShownByDefault() {
        assertTrue(PoiCategory.BAKERY in PoiCategory.DEFAULT)
    }

    @Test
    fun parsesNodesAndWaysAndBuildsSnippet() {
        val body = """{"elements":[
          {"type":"node","id":1,"lat":51.5,"lon":-0.1,"tags":{"name":"Luigi","amenity":"restaurant",
            "cuisine":"italian;pizza","opening_hours":"Mo-Su 11:00-22:00","website":"https://example.com"}},
          {"type":"way","id":2,"center":{"lat":51.6,"lon":-0.2},"tags":{"name":"Corner Cafe","amenity":"cafe"}},
          {"type":"node","id":3,"lat":51.7,"lon":-0.3,"tags":{"amenity":"bar"}}
        ]}"""

        val pois = OverpassSource.parse(body)

        assertEquals(2, pois.size)
        val luigi = pois[0]
        assertEquals("osm:node:1", luigi.id)
        assertEquals(PoiCategory.RESTAURANT, luigi.category)
        assertEquals("Italian, Pizza · Restaurant · Hours: Mo-Su 11:00-22:00", luigi.snippet)
        assertEquals(10.0 + 5 + 5 + 3, luigi.interest, 0.001)
        assertEquals(PoiCategory.CAFE, pois[1].category)
        assertEquals(51.6, pois[1].location.lat, 0.0001)
    }
}
