package com.neargems.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** The N, E, S and W labels sit where those directions are, measured from the top of the pointer. */
class CompassTest {

    @Test
    fun withThePhoneFacingNorthEveryPointIsAtItsOwnBearing() {
        assertEquals(0f, pointerAngle(CompassPoint.NORTH, 0f), 0.001f)
        assertEquals(90f, pointerAngle(CompassPoint.EAST, 0f), 0.001f)
        assertEquals(180f, pointerAngle(CompassPoint.SOUTH, 0f), 0.001f)
        assertEquals(270f, pointerAngle(CompassPoint.WEST, 0f), 0.001f)
    }

    @Test
    fun turningTheLabelsRoundWithThePhone() {
        // Facing east, north is on the left of the pointer: 270 degrees clockwise from the top.
        assertEquals(270f, pointerAngle(CompassPoint.NORTH, 90f), 0.001f)
        // Facing south, north is at the bottom.
        assertEquals(180f, pointerAngle(CompassPoint.NORTH, 180f), 0.001f)
    }

    @Test
    fun anglesWrapAroundAtFullTurns() {
        // Facing 350 degrees, north is 10 degrees clockwise, not -350.
        assertEquals(10f, pointerAngle(CompassPoint.NORTH, 350f), 0.001f)
        assertEquals(0f, pointerAngle(CompassPoint.NORTH, 360f), 0.001f)
    }
}
