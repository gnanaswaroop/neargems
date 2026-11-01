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

    // Lying flat with the top edge to the north, so the screen's top points north when unrotated.
    private val flatTopNorth = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)

    @Test
    fun flatInPortraitPointsTheTopNorth() {
        assertEquals(0f, headingFromRotation(flatTopNorth, screenRotation = 0), 0.01f)
    }

    @Test
    fun flatWithTheScreenRotatedNinetyDegreesPointsTheTopWest() {
        // Landscape with the top of the screen to the west: the screen's top is device -X.
        assertEquals(270f, headingFromRotation(flatTopNorth, screenRotation = 1), 0.01f)
    }

    @Test
    fun flatWithTheScreenUpsideDownPointsTheTopSouth() {
        assertEquals(180f, headingFromRotation(flatTopNorth, screenRotation = 2), 0.01f)
    }

    @Test
    fun flatInReverseLandscapePointsTheTopEast() {
        // Reverse landscape: the top of the screen is device +X, which faces east here.
        assertEquals(90f, headingFromRotation(flatTopNorth, screenRotation = 3), 0.01f)
    }

    @Test
    fun heldUprightInLandscapeStillFollowsTheBackCamera() {
        // The camera does not turn with the screen, so rotating the display does not move the heading.
        val facingEast = floatArrayOf(0f, 0f, -1f, 0f, 0f, 0f, 0f, 1f, 0f)
        assertEquals(90f, headingFromRotation(facingEast, screenRotation = 1), 0.01f)
    }

    @Test
    fun aPhoneRolledSidewaysInAMountStillFacesEast() {
        // Back camera pointing east, top edge tipped 30 degrees to the north. The camera is the
        // longer horizontal part, so it sets the heading. Adding the tipped top edge gave 63 degrees.
        assertEquals(90f, headingFromRotation(floatArrayOf(0f, 0f, -1f, 0f, 0.5f, 0f, 0f, 0.87f, 0f)), 0.5f)
    }
}
