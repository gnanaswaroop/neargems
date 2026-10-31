package com.neargems.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SunTest {

    private val london = LatLon(51.5074, -0.1278)

    private fun at(utc: String): Long = Instant.parse(utc).toEpochMilli()

    @Test
    fun atMidDayInJuneTheSunIsDueSouthAndHigh() {
        // Solar noon in London on the June solstice is close to 12:00 UTC.
        val sun = sunPosition(london, at("2026-06-21T12:00:00Z"))

        assertEquals(180.0, sun.azimuthDegrees, 3.0)
        // 90 degrees minus the latitude, plus the solstice declination of about 23.4 degrees.
        assertEquals(61.9, sun.elevationDegrees, 1.0)
        assertTrue(sun.isAboveHorizon)
    }

    @Test
    fun atSunriseInJuneTheSunRisesInTheNorthEast() {
        // About 04:45 BST, 03:45 UTC.
        val sun = sunPosition(london, at("2026-06-21T03:45:00Z"))

        assertTrue("azimuth ${sun.azimuthDegrees}", sun.azimuthDegrees in 40.0..65.0)
        assertTrue("elevation ${sun.elevationDegrees}", sun.elevationDegrees in -3.0..1.0)
    }

    @Test
    fun atMidnightInLondonTheSunIsBelowTheHorizon() {
        val sun = sunPosition(london, at("2026-06-21T00:00:00Z"))
        assertFalse(sun.isAboveHorizon)
    }

    @Test
    fun azimuthIsAlwaysBetweenZeroAndAFullTurn() {
        for (hour in 0 until 24) {
            val sun = sunPosition(london, at("2026-03-01T%02d:00:00Z".format(hour)))
            assertTrue(sun.azimuthDegrees in 0.0..360.0)
        }
    }
}
