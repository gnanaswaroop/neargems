package com.neargems.data

import com.neargems.support.FixedLocation
import com.neargems.support.Fixtures
import com.neargems.support.MemoryStore
import com.neargems.support.SilentNarrator
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Changing the search radius searches again. The places already shown stay on screen, the new
 * ones from the first source to answer appear while the slower source is still running, and the
 * search is marked as running until both have answered.
 */
class SearchTrickleTest {

    private val overpassCalls = AtomicInteger()
    private val wikipediaCalls = AtomicInteger()
    private val wikipediaGate = CompletableDeferred<Unit>()

    private val twoPlaces = """{"elements":[
        {"type":"node","id":1,"lat":51.5000,"lon":-0.1200,"tags":{"name":"Corner Cafe","amenity":"cafe"}},
        {"type":"node","id":2,"lat":51.5045,"lon":-0.1200,"tags":{"name":"Luigi","amenity":"restaurant"}}
    ]}"""

    /** The same two places plus one about 1.3 km away, which only a wider search finds. */
    private val threePlaces = """{"elements":[
        {"type":"node","id":1,"lat":51.5000,"lon":-0.1200,"tags":{"name":"Corner Cafe","amenity":"cafe"}},
        {"type":"node","id":2,"lat":51.5045,"lon":-0.1200,"tags":{"name":"Luigi","amenity":"restaurant"}},
        {"type":"node","id":3,"lat":51.5120,"lon":-0.1200,"tags":{"name":"Far Tavern","amenity":"cafe"}}
    ]}"""

    private fun slowWikipediaClient() = HttpClient(
        MockEngine { request ->
            val body = if (request.url.host == "overpass-api.de") {
                if (overpassCalls.incrementAndGet() == 1) twoPlaces else threePlaces
            } else {
                // The second Wikipedia search is held until the test lets it through.
                if (wikipediaCalls.incrementAndGet() == 2) wikipediaGate.await()
                """{"query":{"geosearch":[]}}"""
            }
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        },
    )

    @Test
    fun aWiderSearchKeepsTheOldPlacesAndLetsNewOnesTrickleIn() = runBlocking {
        val http = Http(slowWikipediaClient())
        val settings = SettingsStore(MemoryStore()) { 0L }
        val repository = PoiRepository(
            settings = settings,
            finder = PoiFinder(OverpassSource(http), WikipediaSource(http, language = "en")),
            location = FixedLocation(Fixtures.CAFE),
            narrator = SilentNarrator(),
        )

        // The first search finishes with the two places close by.
        launch(Dispatchers.Default) { repository.refresh(announce = false) }
        awaitUntil { repository.state.value.let { it.current != null && !it.searching } }
        assertEquals(setOf("Corner Cafe", "Luigi"), shown(repository))

        // Widen the search. Overpass answers at once; Wikipedia is held.
        settings.update { it.copy(radiusMeters = 2000) }
        launch(Dispatchers.Default) { repository.refresh(announce = false) }
        awaitUntil { shown(repository).contains("Far Tavern") }

        val midSearch = repository.state.value
        assertTrue("the search should still be running", midSearch.searching)
        assertEquals(setOf("Corner Cafe", "Luigi", "Far Tavern"), shown(repository))

        // Let the slow source finish: the search ends and nothing disappears.
        wikipediaGate.complete(Unit)
        awaitUntil { !repository.state.value.searching }
        assertEquals(setOf("Corner Cafe", "Luigi", "Far Tavern"), shown(repository))
    }

    private fun shown(repository: PoiRepository): Set<String> {
        val state = repository.state.value
        return (listOfNotNull(state.current) + state.upcoming).map { it.poi.name }.toSet()
    }

    private fun awaitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (!condition() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
