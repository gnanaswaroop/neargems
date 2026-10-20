package com.neargems.app

import com.neargems.domain.LatLon
import com.neargems.domain.bearingDegrees
import com.neargems.platform.HeadingSource
import com.neargems.platform.LocationSource
import com.neargems.support.MemoryStore
import com.neargems.support.NoMaps
import com.neargems.support.SilentNarrator
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

/**
 * End to end, on Oxford Street in central London: the real container, repository, ranking and
 * compass, with GPS and the compass scripted by the test. Walking or driving east past the places
 * on the street makes some drop out of the list and others come in, and the compass follows the
 * turn. The places are placed on the real street, the way they sit on a map.
 */
class LiveWalkE2ETest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun walkingEastThenNorthEastSwapsPlacesAndTurnsTheCompass() {
        val fixes = MutableSharedFlow<LatLon>(extraBufferCapacity = 16)
        val headings = MutableSharedFlow<Float>(extraBufferCapacity = 16)
        val container = container(fixes, headings)
        container.updateSettings { it.copy(radiusMeters = RADIUS_METRES) }
        container.setVisible(true)
        awaitSubscribed(fixes, headings)

        // Standing at Oxford Circus, facing east: the cafe to the west is close, the bistro is not yet.
        headings.tryEmit(EAST)
        awaitUntil { container.headingDegrees.value == EAST }
        fixes.tryEmit(START)
        awaitPosition(container, START)
        assertEquals(setOf(CAFE_WEST), shown(container))

        // Walk east along Oxford Street, then turn north-east towards the bistro.
        var bearingBeforeTurn = 0.0
        for (step in 1..WALK_STEPS) {
            val position = walkingPosition(step)
            if (step == TURN_STEP) headings.tryEmit(NORTH_EAST)
            fixes.tryEmit(position)
            awaitPosition(container, position)

            if (step == TURN_STEP - 1) {
                assertEquals(EAST, container.headingDegrees.value)
                bearingBeforeTurn = bearingDegrees(position, BISTRO)
            }
            if (step == TURN_STEP) {
                awaitUntil { container.headingDegrees.value == NORTH_EAST }
                assertEquals(NORTH_EAST, container.headingDegrees.value)
                assertTrue("the bistro should be in range by now", shown(container).contains(BISTRO))
            }
            if (step == WALK_STEPS) {
                assertFalse("the cafe should have dropped out of range", shown(container).contains(CAFE_WEST))
                val bearingAfterTurn = bearingDegrees(position, BISTRO)
                assertTrue(
                    "the direction to the bistro should swing as the walk turns " +
                        "(was ${bearingBeforeTurn.toInt()}°, now ${bearingAfterTurn.toInt()}°)",
                    angleBetween(bearingBeforeTurn, bearingAfterTurn) > MIN_COMPASS_SWING,
                )
            }
        }
    }

    @Test
    fun drivingAlongTheSameStreetSwapsPlacesQuickly() {
        val fixes = MutableSharedFlow<LatLon>(extraBufferCapacity = 16)
        val headings = MutableSharedFlow<Float>(extraBufferCapacity = 16)
        val container = container(fixes, headings)
        container.updateSettings { it.copy(radiusMeters = RADIUS_METRES) }
        container.setVisible(true)
        awaitSubscribed(fixes, headings)
        headings.tryEmit(EAST)
        awaitUntil { container.headingDegrees.value == EAST }

        fixes.tryEmit(START)
        awaitPosition(container, START)
        assertEquals(setOf(CAFE_WEST), shown(container))

        // A car covers about 400 m between fixes: the bistro comes in at the first fix,
        // and the cafe is behind the car by the second.
        val first = LatLon(START.lat, START.lon + DRIVE_STEP_DEG)
        fixes.tryEmit(first)
        awaitPosition(container, first)
        assertEquals(setOf(CAFE_WEST, BISTRO), shown(container))

        val second = LatLon(START.lat, START.lon + 2 * DRIVE_STEP_DEG)
        fixes.tryEmit(second)
        awaitPosition(container, second)
        assertEquals(setOf(BISTRO), shown(container))
    }

    private fun container(fixes: Flow<LatLon>, headings: MutableSharedFlow<Float>): DriveContainer =
        DriveContainer(
            PlatformServices(
                http = streetHttpClient(),
                keyValues = MemoryStore(),
                location = ScriptedLocation(START, fixes),
                headings = ScriptedHeadings(headings),
                narrator = SilentNarrator(),
                maps = NoMaps(),
                carConnected = flowOf(false),
                language = "en",
                clock = { 0L },
            ),
        )

    private fun shown(container: DriveContainer): Set<LatLon> {
        val state = container.state.value
        return (listOfNotNull(state.current) + state.upcoming).map { it.poi.location }.toSet()
    }

    /** One fix per step: east for the first half, then north-east as the road bends. */
    private fun walkingPosition(step: Int): LatLon {
        val east = minOf(step, TURN_STEP - 1)
        val northEast = maxOf(0, step - (TURN_STEP - 1))
        return LatLon(
            START.lat + northEast * NORTH_EAST_LAT_STEP,
            START.lon + (east + northEast) * WALK_LON_STEP,
        )
    }

    private fun awaitSubscribed(fixes: MutableSharedFlow<LatLon>, headings: MutableSharedFlow<Float>) {
        awaitUntil { fixes.subscriptionCount.value > 0 && headings.subscriptionCount.value > 0 }
    }

    private fun awaitPosition(container: DriveContainer, position: LatLon) =
        awaitUntil { container.state.value.position == position }

    private fun awaitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (!condition() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
    }

    private fun angleBetween(a: Double, b: Double): Double {
        val diff = abs(a - b) % 360
        return if (diff > 180) 360 - diff else diff
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val RADIUS_METRES = 500
        const val EAST = 90f
        const val NORTH_EAST = 45f
        const val WALK_STEPS = 10
        const val TURN_STEP = 6
        const val WALK_LON_STEP = 0.0008 // about 55 m east or north-east per fix
        const val NORTH_EAST_LAT_STEP = 0.0005 // the north part of each north-east fix, about 56 m
        const val DRIVE_STEP_DEG = 0.006 // about 415 m east per fix
        const val MIN_COMPASS_SWING = 45.0

        /** Oxford Circus, central London. */
        val START = LatLon(51.5152, -0.1415)

        /** A café on Oxford Street just west of the start. It is in range at first, then drops out. */
        val CAFE_WEST = LatLon(51.5150, -0.1425)

        /** A bistro on Oxford Street to the east. It is out of range at the start and comes into range. */
        val BISTRO = LatLon(51.5165, -0.1330)

        const val CAFE_WEST_NAME = "Oxford Street Bakery"
        const val BISTRO_NAME = "Regent Bistro"

        val OVERPASS_BODY = """{"elements":[
            {"type":"node","id":1,"lat":51.5150,"lon":-0.1425,"tags":{"name":"$CAFE_WEST_NAME","amenity":"cafe"}},
            {"type":"node","id":2,"lat":51.5165,"lon":-0.1330,"tags":{"name":"$BISTRO_NAME","amenity":"restaurant"}}
        ]}"""

        fun streetHttpClient(): HttpClient = HttpClient(
            MockEngine { request ->
                val body = if (request.url.host == "overpass-api.de") OVERPASS_BODY
                else """{"query":{"geosearch":[]}}"""
                respond(
                    content = body,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        )
    }
}

/** GPS fixes scripted by the test. The first fix is also what the app asks for on start. */
private class ScriptedLocation(private val start: LatLon, private val fixes: Flow<LatLon>) : LocationSource {
    override suspend fun current(): LatLon = start
    override fun updates(): Flow<LatLon> = fixes
}

/** Compass readings scripted by the test. */
private class ScriptedHeadings(private val readings: MutableSharedFlow<Float>) : HeadingSource {
    override fun headings(): Flow<Float> = readings.asSharedFlow()
}
