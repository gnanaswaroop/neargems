package com.neargems.data

import com.neargems.app.DriveContainer
import com.neargems.app.PlatformServices
import com.neargems.domain.LatLon
import com.neargems.domain.Poi
import com.neargems.domain.PoiCategory
import com.neargems.domain.RankedPoi
import com.neargems.support.FixedLocation
import com.neargems.support.Fixtures
import com.neargems.support.MemoryStore
import com.neargems.support.NoHeadings
import com.neargems.support.NoMaps
import com.neargems.support.SilentNarrator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
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

class VisitedHistoryTest {

    private val here = LatLon(51.5, -0.12)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun poi(id: String, name: String) = Poi(id, name, PoiCategory.CAFE, here, "", 1.0)

    @Test
    fun newestVisitsComeFirstAndAVisitAgainMovesThePlaceToTheTop() {
        val history = VisitedHistory(MemoryStore())
        history.add(poi("a", "Alpha"), 1_000L)
        history.add(poi("b", "Beta"), 2_000L)
        history.add(poi("a", "Alpha"), 3_000L)

        assertEquals(listOf("a", "b"), history.places.value.map { it.id })
        assertEquals(3_000L, history.places.value.first().atMillis)
    }

    @Test
    fun visitsAreRememberedAfterARestart() {
        val store = MemoryStore()
        VisitedHistory(store).add(poi("a", "Alpha | Bar"), 5_000L)

        val restored = VisitedHistory(store).places.value.single()
        assertEquals("Alpha   Bar", restored.name)
        assertEquals(PoiCategory.CAFE, restored.category)
        assertEquals(here, restored.location)
    }

    @Test
    fun removingAPlaceLeavesTheOthers() {
        val history = VisitedHistory(MemoryStore())
        history.add(poi("a", "Alpha"), 1L)
        history.add(poi("b", "Beta"), 2L)
        history.remove("a")

        assertEquals(listOf("b"), history.places.value.map { it.id })
    }

    @Test
    fun markingVisitedAddsToHistoryAndShowAgainBringsItBack() {
        val container = DriveContainer(
            PlatformServices(
                http = Fixtures.httpClient(),
                keyValues = MemoryStore(),
                location = FixedLocation(Fixtures.CAFE, emptyFlow()),
                headings = NoHeadings(),
                narrator = SilentNarrator(),
                maps = NoMaps(),
                carConnected = flowOf(false),
                language = "en",
                clock = { 9_000L },
            ),
        )
        val cafe = poi("osm:node:9", "Corner Cafe")
        container.markVisited(RankedPoi(cafe, distanceMeters = 10.0, score = 1.0))

        assertEquals(listOf("osm:node:9"), container.visited.value.map { it.id })
        assertTrue(container.settings.visitedIds().contains("osm:node:9"))

        container.showAgain("osm:node:9")

        assertTrue(container.visited.value.isEmpty())
        assertFalse(container.settings.visitedIds().contains("osm:node:9"))
    }
}
