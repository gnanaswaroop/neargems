package com.neargems.support

import com.neargems.app.DriveContainer
import com.neargems.app.PlatformServices
import com.neargems.data.Http
import com.neargems.data.OverpassSource
import com.neargems.data.PoiFinder
import com.neargems.data.PoiRepository
import com.neargems.data.SettingsStore
import com.neargems.data.WikipediaSource
import com.neargems.domain.LatLon
import com.neargems.domain.Poi
import com.neargems.platform.HeadingSource
import com.neargems.platform.KeyValueStore
import com.neargems.platform.LocationSource
import com.neargems.platform.MapOpener
import com.neargems.platform.Narrator
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Two places about 500 m apart, with fake network, GPS, compass, speech and map. Shared by the
 * unit tests, the integration tests and the on-device screen tests.
 */
object Fixtures {
    val CAFE = LatLon(51.5000, -0.1200)
    val RESTAURANT = LatLon(51.5045, -0.1200) // about 500 m north of the cafe
    const val CAFE_NAME = "Corner Cafe"
    const val RESTAURANT_NAME = "Luigi"

    /** Long enough that the snippet folds and shows "Read more". */
    private const val LONG_HOURS =
        "Mo-Fr 07:00-18:00; Sa 08:00-19:00; Su 09:00-17:00; public holidays 10:00-16:00; " +
            "happy hour 16:00-18:00 with half price coffee and cake"

    private val overpassBody = """{"elements":[
        {"type":"node","id":1,"lat":51.5000,"lon":-0.1200,"tags":{"name":"Corner Cafe","amenity":"cafe",
            "opening_hours":"$LONG_HOURS"}},
        {"type":"node","id":2,"lat":51.5045,"lon":-0.1200,"tags":{"name":"Luigi","amenity":"restaurant",
            "cuisine":"italian"}}
    ]}"""

    fun httpClient(): HttpClient = HttpClient(
        MockEngine { request ->
            val body = if (request.url.host == "overpass-api.de") overpassBody
            else """{"query":{"geosearch":[]}}"""
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        },
    )

    /** The repository on its own, for tests that drive it directly. */
    fun repository(location: LatLon? = CAFE): PoiRepository {
        val http = Http(httpClient())
        return PoiRepository(
            settings = SettingsStore(MemoryStore()) { 0L },
            finder = PoiFinder(OverpassSource(http), WikipediaSource(http, language = "en")),
            location = FixedLocation(location),
            narrator = SilentNarrator(),
        )
    }

    /** The whole app model, as the Android entry point builds it, with every platform piece faked. */
    fun driveContainer(location: LatLon? = CAFE, live: Flow<LatLon> = emptyFlow()): DriveContainer =
        DriveContainer(
            PlatformServices(
                http = httpClient(),
                keyValues = MemoryStore(),
                location = FixedLocation(location, live),
                headings = NoHeadings(),
                narrator = SilentNarrator(),
                maps = NoMaps(),
                carConnected = flowOf(false),
                language = "en",
                clock = { 0L },
            ),
        )
}

class MemoryStore : KeyValueStore {
    private val values = mutableMapOf<String, String>()
    override fun getString(key: String): String? = values[key]
    override fun putString(key: String, value: String?) {
        if (value == null) values.remove(key) else values[key] = value
    }
}

/** A fixed position, plus an optional stream of GPS fixes for when the screen is on. */
class FixedLocation(private val fix: LatLon?, private val live: Flow<LatLon> = emptyFlow()) : LocationSource {
    override suspend fun current(): LatLon? = fix
    override fun updates(): Flow<LatLon> = live
}

class SilentNarrator : Narrator {
    override fun speak(text: String) = Unit
    override fun stop() = Unit
}

class NoHeadings : HeadingSource {
    override fun headings(): Flow<Float> = emptyFlow()
}

class NoMaps : MapOpener {
    override fun open(poi: Poi) = Unit
    override fun openUrl(url: String) = Unit
}
