package com.neargems.app

import com.neargems.data.VisitedPlace
import com.neargems.domain.LatLon
import com.neargems.domain.PoiCategory
import com.neargems.platform.MemorySeenStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The counts in Settings match the places seen and visited, and far-away places keep their location. */
class PlaceOverviewTest {

    private val london = LatLon(51.5080, -0.1281)
    private val far = LatLon(53.4808, -2.2426) // Manchester, well outside any search radius

    @Test
    fun countsEveryDiscoveredAndVisitedPlace() {
        val store = MemorySeenStore()
        store.markSeen("osm:node:1", "Corner Cafe", "Café", 1_000L, london)
        store.markSeen("osm:node:2", "Far Bakery", "Bakery", 2_000L, far)
        store.markSeen("osm:node:1", "Corner Cafe", "Café", 3_000L, london) // seen again: still one place
        val visited = listOf(VisitedPlace("osm:node:1", "Corner Cafe", PoiCategory.CAFE, london, 3_000L))

        assertEquals(PlaceOverview(discovered = 2, visited = 1), placeOverview(store.seenPlaces(), visited))
    }

    @Test
    fun aFarAwayPlaceKeepsItsLocationAndLastSeenTime() {
        val store = MemorySeenStore()
        store.markSeen("osm:node:2", "Far Bakery", "Bakery", 1_000L, far)
        store.markSeen("osm:node:2", "Far Bakery", "Bakery", 5_000L, far)

        val place = store.seenPlaces().single()
        assertEquals(far, place.location)
        assertEquals(1_000L, place.firstSeenMillis)
        assertEquals(5_000L, place.lastSeenMillis)
    }

    @Test
    fun aPlaceSeenBeforeLocationsWereKeptHasNoLocation() {
        val store = MemorySeenStore()
        store.markSeen("osm:node:3", "Old Pub", "Bar", 1_000L)

        assertNull(store.seenPlaces().single().location)
    }
}
