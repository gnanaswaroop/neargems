package com.neargems.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PoiRankerTest {
    private val here = LatLon(51.5, -0.12)

    /** A place [metersNorth] north of [here]. */
    private fun poi(id: String, interest: Double, metersNorth: Double) = Poi(
        id = id,
        name = id,
        category = PoiCategory.RESTAURANT,
        location = LatLon(here.lat + metersNorth / 111_320.0, here.lon),
        snippet = "",
        interest = interest,
    )

    @Test
    fun nearbyPlaceCanBeatAFartherOneWithMoreInterest() {
        val ranked = PoiRanker.rank(
            listOf(poi("far", 40.0, 900.0), poi("near", 20.0, 100.0)),
            here,
            radiusMeters = 1000,
            excludedIds = emptySet(),
        )
        assertEquals(listOf("near", "far"), ranked.map { it.poi.id })
    }

    @Test
    fun excludedAndOutOfRadiusPlacesAreDropped() {
        val ranked = PoiRanker.rank(
            listOf(poi("a", 10.0, 100.0), poi("b", 10.0, 100.0), poi("c", 10.0, 5000.0)),
            here,
            radiusMeters = 1000,
            excludedIds = setOf("b"),
        )
        assertEquals(listOf("a"), ranked.map { it.poi.id })
    }

    @Test
    fun distanceMatchesHaversine() {
        // 0.001 degrees of latitude is about 111 m.
        assertEquals(111.0, distanceMeters(LatLon(0.0, 0.0), LatLon(0.001, 0.0)), 1.0)
    }

    @Test
    fun formatsDistancesForScreenAndSpeech() {
        assertEquals("250 m", formatDistance(248.0))
        assertEquals("1.4 km", formatDistance(1400.0))
        assertEquals("250 meters", spokenDistance(248.0))
        assertEquals("1.4 kilometers", spokenDistance(1400.0))
    }
}
