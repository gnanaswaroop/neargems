package com.neargems.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeywordsTest {

    private val here = LatLon(51.5, -0.12)

    private fun poi(
        id: String,
        name: String,
        snippet: String = "",
        category: PoiCategory = PoiCategory.LANDMARK,
    ) = Poi(id, name, category, here, snippet, interest = 1.0)

    @Test
    fun typedKeywordsAreSplitTrimmedAndLowerCased() {
        assertEquals(listOf("temple", "church", "waterfall"), parseKeywords(" Temple, church ,,WATERFALL, temple"))
        assertEquals(emptyList<String>(), parseKeywords("  ,  "))
    }

    @Test
    fun aPlaceMatchesWhenItsNameOrSnippetHasAKeyword() {
        val temple = poi("1", "Shiva Temple")
        val cafe = poi("2", "Corner Bakery", snippet = "Coffee and cake")

        assertTrue(temple.matchesKeywords(listOf("temple")))
        assertTrue(cafe.matchesKeywords(listOf("cake")))
        assertFalse(cafe.matchesKeywords(listOf("temple")))
    }

    @Test
    fun noKeywordsMatchEveryPlace() {
        assertTrue(poi("1", "Anything").matchesKeywords(emptyList()))
    }

    @Test
    fun stationsAndStopsAreNeverLandmarks() {
        assertTrue(poi("1", "Oxford Circus Station").isTransportStop())
        assertTrue(poi("2", "Peckham Rye railway station").isTransportStop())
        assertFalse(poi("3", "Oxford Street").isTransportStop())
    }

    @Test
    fun aStationIsNotASuggestionEvenWhenItsCategoryIsWanted() {
        val ranked = PoiRanker.rank(
            listOf(poi("1", "Oxford Circus Station"), poi("2", "Marble Arch")),
            from = here,
            radiusMeters = 1000,
            excludedIds = emptySet(),
        )
        assertEquals(listOf("2"), ranked.map { it.poi.id })
    }

    @Test
    fun theRankerKeepsOnlyPlacesThatMatchTheKeywords() {
        val ranked = PoiRanker.rank(
            listOf(poi("1", "Shiva Temple"), poi("2", "Old Church"), poi("3", "Market Hall")),
            from = here,
            radiusMeters = 1000,
            excludedIds = emptySet(),
            keywords = parseKeywords("temple, church"),
        )
        assertEquals(setOf("1", "2"), ranked.map { it.poi.id }.toSet())
    }
}
