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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Drives the repository along a GPX track, the same track the emulator replays with
 * scripts/play-gpx.ps1. The route is read from src/test/resources/routes, so the test and the
 * video use the same fixes.
 */
class GpxRouteTest {

    private val route = readGpx("routes/whitehall-drive.gpx")

    @Test
    fun theRouteFileHasAMovingTrack() {
        assertTrue("expected a track with many points, got ${route.size}", route.size > 30)
        // Each fix is a short step on from the one before: a drive, not a jump.
        route.zipWithNext().forEach { (a, b) -> assertTrue(distanceMeters(a, b) < 60.0) }
    }

    @Test
    fun theCardFollowsTheDriveAndStopsTheReadingWhenItChanges() = runBlocking {
        val narrator = CountingNarrator()
        val repository = repository(narrator)
        repository.refresh(announce = true)
        assertEquals(1, narrator.spoken.size)
        val names = mutableSetOf(repository.state.value.current!!.poi.name)

        for (fix in route.drop(1)) {
            repository.onPosition(fix, announce = false)
            val card = repository.state.value.current
            if (card == null) break
            names += card.poi.name
            // The distance on the card follows the car on every fix.
            assertEquals(distanceMeters(fix, card.poi.location), card.distanceMeters, 0.5)
        }

        // The card changed during the drive, and reading the first place stopped when it did.
        assertEquals(setOf("Trafalgar Cafe", "Bridge Cafe"), names)
        assertTrue("reading should stop when the card changes", narrator.stops >= 1)
    }

    private fun readGpx(resource: String): List<LatLon> {
        val file = File(javaClass.classLoader!!.getResource(resource)!!.toURI())
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val points = document.getElementsByTagName("trkpt")
        return (0 until points.length).map { index ->
            val element = points.item(index) as org.w3c.dom.Element
            LatLon(element.getAttribute("lat").toDouble(), element.getAttribute("lon").toDouble())
        }
    }

    private fun repository(narrator: Narrator): PoiRepository {
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
                },
            ),
        )
        val location = object : LocationSource {
            override suspend fun current(): LatLon? = route.first()
            override fun updates() = emptyFlow<LatLon>()
        }
        return PoiRepository(
            settings = SettingsStore(MemoryStore(), clock = { 0L }),
            finder = PoiFinder(OverpassSource(http), WikipediaSource(http, language = "en")),
            location = location,
            narrator = narrator,
        )
    }

    /** A cafe at Trafalgar Square, where the drive starts, and one by Westminster Bridge, where it ends. */
    private val overpassBody = """{"elements":[
        {"type":"node","id":1,"lat":51.5081,"lon":-0.1283,"tags":{"name":"Trafalgar Cafe","amenity":"cafe"}},
        {"type":"node","id":2,"lat":51.5009,"lon":-0.1236,"tags":{"name":"Bridge Cafe","amenity":"cafe"}}
    ]}"""

    private class CountingNarrator : Narrator {
        val spoken = mutableListOf<String>()
        var stops = 0
        override fun speak(text: String) {
            spoken += text
        }

        override fun stop() {
            stops++
        }
    }

    private class MemoryStore : KeyValueStore {
        private val values = mutableMapOf<String, String>()
        override fun getString(key: String): String? = values[key]
        override fun putString(key: String, value: String?) {
            if (value == null) values.remove(key) else values[key] = value
        }
    }
}
