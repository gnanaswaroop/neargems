package com.nearwise.data

import com.nearwise.domain.LatLon
import com.nearwise.domain.bearingDegrees
import com.nearwise.domain.compassPoint
import com.nearwise.domain.driveMinutes
import com.nearwise.domain.walkMinutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WikipediaPhotoTest {

    @Test
    fun parsesThumbnailAndFileName() {
        val body = """{"query":{"pages":{"42":{"pageid":42,"extract":"Tower Bridge is a bridge.",
            "pageimage":"Tower Bridge from Shad Thames.jpg",
            "thumbnail":{"source":"https://upload.wikimedia.org/x/640px-Tower_Bridge.jpg","width":640,"height":400}}}}}"""

        val page = WikipediaSource.parseExtracts(body).getValue(42)
        val photo = WikipediaSource.photoFor(page, "en")!!

        assertEquals("https://upload.wikimedia.org/x/640px-Tower_Bridge.jpg", photo.imageUrl)
        assertEquals("https://en.wikipedia.org/wiki/File:Tower_Bridge_from_Shad_Thames.jpg", photo.fileUrl)
    }

    @Test
    fun pagesWithoutAnImageHaveNoPhoto() {
        val body = """{"query":{"pages":{"7":{"pageid":7,"extract":"Some place."}}}}"""
        assertNull(WikipediaSource.photoFor(WikipediaSource.parseExtracts(body).getValue(7), "en"))
    }

    @Test
    fun bearingAndCompassPointsAreRight() {
        val origin = LatLon(51.5, -0.12)
        assertEquals(0.0, bearingDegrees(origin, LatLon(51.6, -0.12)), 0.5)   // due north
        assertEquals(90.0, bearingDegrees(origin, LatLon(51.5, -0.10)), 0.5)  // due east
        assertEquals("N", compassPoint(350.0))
        assertEquals("NE", compassPoint(45.0))
        assertEquals("W", compassPoint(270.0))
    }

    @Test
    fun travelTimesAreRoughAndAtLeastOneMinute() {
        assertEquals(1, walkMinutes(20.0))
        assertEquals(12, walkMinutes(1000.0))
        assertEquals(2, driveMinutes(1000.0))
    }
}
