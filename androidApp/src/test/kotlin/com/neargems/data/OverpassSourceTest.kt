package com.neargems.data

import com.neargems.domain.PoiCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class OverpassSourceTest {

    @Test
    fun parsesNodesAndWaysAndBuildsSnippet() {
        val body = """{"elements":[
          {"type":"node","id":1,"lat":51.5,"lon":-0.1,"tags":{"name":"Luigi","amenity":"restaurant",
            "cuisine":"italian;pizza","opening_hours":"Mo-Su 11:00-22:00","website":"https://example.com"}},
          {"type":"way","id":2,"center":{"lat":51.6,"lon":-0.2},"tags":{"name":"Corner Cafe","amenity":"cafe"}},
          {"type":"node","id":3,"lat":51.7,"lon":-0.3,"tags":{"amenity":"bar"}}
        ]}"""

        val pois = OverpassSource.parse(body)

        assertEquals(2, pois.size)
        val luigi = pois[0]
        assertEquals("osm:node:1", luigi.id)
        assertEquals(PoiCategory.RESTAURANT, luigi.category)
        assertEquals("Italian, Pizza · Restaurant · Hours: Mo-Su 11:00-22:00", luigi.snippet)
        assertEquals(10.0 + 5 + 5 + 3, luigi.interest, 0.001)
        assertEquals(PoiCategory.CAFE, pois[1].category)
        assertEquals(51.6, pois[1].location.lat, 0.0001)
    }
}
