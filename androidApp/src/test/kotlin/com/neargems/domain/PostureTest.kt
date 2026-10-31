package com.neargems.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PostureTest {

    @Test
    fun handHeldUprightIsPortraitHandHeld() {
        assertEquals(PhonePosture(Orientation.PORTRAIT, Holding.HAND_HELD), classifyPosture(0.2f, 9.7f, false, 0, 1.5))
    }

    @Test
    fun turnedOnItsSideIsLandscapeHandHeld() {
        assertEquals(PhonePosture(Orientation.LANDSCAPE, Holding.HAND_HELD), classifyPosture(9.6f, 0.5f, false, 0, 1.5))
    }

    @Test
    fun mountedInACarDisplayIsMountedInEitherOrientation() {
        assertEquals(PhonePosture(Orientation.PORTRAIT, Holding.MOUNTED), classifyPosture(0.1f, 9.8f, true, 0, 0.0))
        assertEquals(PhonePosture(Orientation.LANDSCAPE, Holding.MOUNTED), classifyPosture(9.8f, 0.1f, true, 0, 0.0))
    }

    @Test
    fun steadyWhileTheVehicleMovesIsMounted() {
        assertEquals(PhonePosture(Orientation.PORTRAIT, Holding.MOUNTED), classifyPosture(0.1f, 9.8f, false, 25, 12.0))
    }

    @Test
    fun steadyWhileStationaryIsNotMountedYet() {
        assertEquals(PhonePosture(Orientation.PORTRAIT, Holding.HAND_HELD), classifyPosture(0.1f, 9.8f, false, 60, 0.0))
    }

    @Test
    fun aShortStillPeriodOnTheMoveIsNotMounted() {
        assertEquals(PhonePosture(Orientation.PORTRAIT, Holding.HAND_HELD), classifyPosture(0.1f, 9.8f, false, 5, 12.0))
    }
}
