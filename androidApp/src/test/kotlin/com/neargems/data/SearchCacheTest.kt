package com.neargems.data

import com.neargems.domain.LatLon
import com.neargems.domain.Photo
import com.neargems.domain.Poi
import com.neargems.domain.PoiCategory
import com.neargems.platform.KeyValueStore
import com.neargems.platform.LocationSource
import com.neargems.platform.Narrator
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchCacheTest {

    private val origin = LatLon(51.5080, -0.1281)
    private val cafe = Poi(
        id = "osm:node:1",
        name = "Corner Cafe",
        category = PoiCategory.CAFE,
        location = LatLon(51.5081, -0.1283),
        snippet = "Café · Hours: Mo-Fr 07:00-18:00",
        interest = 15.0,
        photo = Photo("https://example.com/cafe.jpg", "https://example.com/file"),
        link = "https://en.wikipedia.org/wiki/Corner_Cafe",
    )

    @Test
    fun aSavedSearchComesBackWithItsPlacesAndSettings() {
        var now = 1_000L
        val cache = SearchCache(MemoryStore(), clock = { now })

        cache.save(CachedSearch(origin, setOf(PoiCategory.CAFE), 1000, listOf("church"), listOf(cafe)))

        val loaded = cache.load()!!
        assertEquals(origin, loaded.origin)
        assertEquals(setOf(PoiCategory.CAFE), loaded.categories)
        assertEquals(1000, loaded.radiusMeters)
        assertEquals(listOf("church"), loaded.keywords)
        assertEquals(cafe, loaded.places.single())
    }

    @Test
    fun aSearchOlderThanTwelveHoursIsNotUsed() {
        var now = 0L
        val cache = SearchCache(MemoryStore(), clock = { now })
        cache.save(CachedSearch(origin, setOf(PoiCategory.CAFE), 1000, emptyList(), listOf(cafe)))

        now = SearchCache.MAX_AGE_MS
        assertTrue(cache.load() != null)
        now = SearchCache.MAX_AGE_MS + 1
        assertNull(cache.load())
    }

    @Test
    fun aSecondOpenAtTheSamePlaceShowsTheSavedPlacesWithoutSearching() = runBlocking {
        val store = MemoryStore()
        var requests = 0
        val client = HttpClient(
            MockEngine {
                requests++
                respond(
                    content = """{"elements":[{"type":"node","id":1,"lat":51.5081,"lon":-0.1283,
                        "tags":{"name":"Corner Cafe","amenity":"cafe"}}]}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        )
        val http = Http(client)
        val location = object : LocationSource {
            override suspend fun current(): LatLon? = origin
            override fun updates() = emptyFlow<LatLon>()
        }
        val narrator = object : Narrator {
            override fun speak(text: String) = Unit
            override fun stop() = Unit
        }

        // The first open searches and saves the result.
        val first = repository(store, http, location, narrator)
        first.refresh(announce = false)
        assertEquals("Corner Cafe", first.state.value.current?.poi?.name)
        val searchesBefore = requests
        assertTrue(searchesBefore > 0)

        // A new open, with nothing in memory, shows the saved place at once and does not search again.
        val second = repository(store, http, location, narrator)
        second.refresh(announce = false)
        assertEquals("Corner Cafe", second.state.value.current?.poi?.name)
        assertEquals(searchesBefore, requests)
    }

    private fun repository(store: KeyValueStore, http: Http, location: LocationSource, narrator: Narrator): PoiRepository =
        PoiRepository(
            settings = SettingsStore(store, clock = { 0L }),
            finder = PoiFinder(OverpassSource(http), WikipediaSource(http, language = "en")),
            location = location,
            narrator = narrator,
            searchCache = SearchCache(store, clock = { 0L }),
        )

    private class MemoryStore : KeyValueStore {
        private val values = mutableMapOf<String, String>()
        override fun getString(key: String): String? = values[key]
        override fun putString(key: String, value: String?) {
            if (value == null) values.remove(key) else values[key] = value
        }
    }
}
