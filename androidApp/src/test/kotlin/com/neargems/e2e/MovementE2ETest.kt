package com.neargems.e2e

import com.neargems.data.Http
import com.neargems.data.OverpassSource
import com.neargems.data.PoiFinder
import com.neargems.data.PoiRepository
import com.neargems.data.SettingsStore
import com.neargems.data.WikipediaSource
import com.neargems.domain.LatLon
import com.neargems.domain.distanceMeters
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
import org.junit.Test

/**
 * End to end, without a device: a walker moves from a cafe towards a restaurant, one fix at a time.
 * Real HTTP parsing, real ranking and the real repository run; only the network, GPS and speech are fakes.
 */
class MovementE2ETest {

    private val cafeSpot = LatLon(51.5000, -0.1200)
    private val restaurantSpot = LatLon(51.5045, -0.1200) // about 500 m north of the cafe

    /** Overpass answer with one cafe at [cafeSpot] and one restaurant at [restaurantSpot]. */
    private val overpassBody = """{"elements":[
        {"type":"node","id":1,"lat":51.5000,"lon":-0.1200,"tags":{"name":"Corner Cafe","amenity":"cafe"}},
        {"type":"node","id":2,"lat":51.5045,"lon":-0.1200,"tags":{"name":"Luigi","amenity":"restaurant",
            "cuisine":"italian","website":"https://example.com"}}
    ]}"""

    @Test
    fun walkingFromTheCafeToTheRestaurantSwapsTheCardAndKeepsDistanceLive() = runBlocking {
        val repository = repository()

        repository.refresh(announce = false)
        assertEquals("Corner Cafe", repository.state.value.current?.poi?.name)

        var swappedAtStep: Int? = null
        for (step in 1..STEPS) {
            // Each step is one GPS fix, about 50 m further along the walk.
            val fix = LatLon(
                cafeSpot.lat + (restaurantSpot.lat - cafeSpot.lat) * step / STEPS,
                cafeSpot.lon,
            )
            repository.onPosition(fix, announce = false)

            val top = repository.state.value.current!!
            if (top.poi.name == "Luigi" && swappedAtStep == null) swappedAtStep = step

            // The distance on the card follows the walker on every fix.
            assertEquals(distanceMeters(fix, top.poi.location), top.distanceMeters, 0.5)
        }

        // Halfway the cafe is still closer in score; past about 100 m the restaurant takes the top card.
        assertEquals(3, swappedAtStep)
        assertEquals("Luigi", repository.state.value.current?.poi?.name)
    }

    @Test
    fun aCardDisappearsWhenItIsDismissedAndNothingElseIsNearby() = runBlocking {
        val repository = repository()
        repository.refresh(announce = false)

        repository.next(announce = false) // dismiss the cafe, the restaurant is 500 m away and still shown
        assertEquals("Luigi", repository.state.value.current?.poi?.name)

        repository.next(announce = false) // dismiss the restaurant too
        assertNull(repository.state.value.current)
        assertEquals("No more suggestions nearby.", repository.state.value.message)
    }

    private fun repository(): PoiRepository {
        val http = Http(
            HttpClient(
                MockEngine { request ->
                    val body = if (request.url.host == "overpass-api.de") overpassBody
                    else """{"query":{"geosearch":[]}}"""
                    respond(
                        content = body,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            )
        )
        val location = object : LocationSource {
            override suspend fun current(): LatLon? = cafeSpot
            override fun updates() = emptyFlow<LatLon>()
        }
        val narrator = object : Narrator {
            override fun speak(text: String) = Unit
            override fun stop() = Unit
        }
        return PoiRepository(
            settings = SettingsStore(MemoryStore(), clock = { 0L }),
            finder = PoiFinder(OverpassSource(http), WikipediaSource(http, language = "en")),
            location = location,
            narrator = narrator,
        )
    }

    private class MemoryStore : KeyValueStore {
        private val values = mutableMapOf<String, String>()
        override fun getString(key: String): String? = values[key]
        override fun putString(key: String, value: String?) {
            if (value == null) values.remove(key) else values[key] = value
        }
    }

    private companion object {
        const val STEPS = 10
    }
}
