package com.neargems.android.platform

import org.junit.Assert.assertEquals
import org.junit.Test

/** The phone's heading is the way it faces: the top edge when flat, the back camera when upright. */
class HeadingTest {

    @Test
    fun flatWithTheTopEdgeToTheNorthIsZeroDegrees() {
        assertEquals(0f, headingFromRotation(floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)), 0.01f)
    }

    @Test
    fun flatWithTheTopEdgeToTheEastIsNinetyDegrees() {
        assertEquals(90f, headingFromRotation(floatArrayOf(0f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 1f)), 0.01f)
    }

    @Test
    fun heldUprightFacingEastGivesNinetyDegrees() {
        // Top edge up, back camera pointing east: the top edge has no horizontal part, so the camera decides.
        assertEquals(90f, headingFromRotation(floatArrayOf(0f, 0f, -1f, 0f, 0f, 0f, 0f, 1f, 0f)), 0.01f)
    }

    @Test
    fun heldUprightFacingNorthGivesZeroDegrees() {
        assertEquals(0f, headingFromRotation(floatArrayOf(1f, 0f, 0f, 0f, 0f, -1f, 0f, 1f, 0f)), 0.01f)
    }

    @Test
    fun aPhoneRolledSidewaysInAMountStillFacesEast() {
        // Back camera pointing east, top edge tipped 30 degrees to the north. The camera is the
        // longer horizontal part, so it sets the heading. Adding the tipped top edge gave 63 degrees.
        assertEquals(90f, headingFromRotation(floatArrayOf(0f, 0f, -1f, 0f, 0.5f, 0f, 0f, 0.87f, 0f)), 0.5f)
    }
}
