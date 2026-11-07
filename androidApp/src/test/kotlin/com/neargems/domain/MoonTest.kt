package com.neargems.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MoonTest {

    private val london = LatLon(51.5080, -0.1281)

    @Test
    fun atANewMoonTheMoonIsCloseToTheSun() {
        // 6 January 2000, 18:14 UT: a new moon, so the moon and the sun are close together in the sky.
        val time = 947182440000L
        val sun = sunPosition(london, time)
        val moon = moonPosition(london, time)

        assertTrue("azimuths differ by ${azimuthGap(sun.azimuthDegrees, moon.azimuthDegrees)}",
            azimuthGap(sun.azimuthDegrees, moon.azimuthDegrees) < 15)
        assertTrue("elevations differ by ${abs(sun.elevationDegrees - moon.elevationDegrees)}",
            abs(sun.elevationDegrees - moon.elevationDegrees) < 15)
    }

    @Test
    fun theMoonStaysWithinItsRangesOverAMonth() {
        val start = 1_781_000_000_000L
        for (hour in 0 until 24 * 30 step 3) {
            val moon = moonPosition(london, start + hour * 3_600_000L)
            assertTrue(moon.azimuthDegrees >= 0 && moon.azimuthDegrees < 360)
            assertTrue(moon.elevationDegrees >= -90 && moon.elevationDegrees <= 90)
        }
    }

    @Test
    fun theSunIsShownWhileItIsUpAndTheMoonAfterwards() {
        val sunUp = SunPosition(azimuthDegrees = 120.0, elevationDegrees = 30.0)
        val sunDown = SunPosition(azimuthDegrees = 300.0, elevationDegrees = -20.0)
        val moon = SunPosition(azimuthDegrees = 45.0, elevationDegrees = 25.0)

        assertEquals(SkyBody.SUN, pointerSky(sunUp, moon)?.body)
        assertEquals(SkyBody.MOON, pointerSky(sunDown, moon)?.body)
        assertSame(moon, pointerSky(sunDown, moon)?.position)
    }

    @Test
    fun withNoFixThereIsNothingToShow() {
        assertNull(pointerSky(null, null))
    }

    /** The smaller angle between two bearings, so 359 and 1 are 2 degrees apart. */
    private fun azimuthGap(a: Double, b: Double): Double {
        val d = abs(a - b) % 360
        return if (d > 180) 360 - d else d
    }
}
