package com.neargems.app

import com.neargems.domain.LatLon
import com.neargems.support.Fixtures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * The container with real repository, settings and HTTP parsing. Only the platform is faked, so
 * the test location, the GPS fixes and Back all run the same path as on a phone.
 *
 * Fetching places goes through the fake HTTP client, which answers on another thread, so these
 * tests wait for the state to settle rather than checking it straight after the call.
 */
class DriveContainerTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun aChosenTestLocationReplacesTheGpsFix() {
        val container = Fixtures.driveContainer(location = Fixtures.CAFE)
        container.refresh()
        awaitTop(container, Fixtures.CAFE_NAME)

        container.updateSettings { it.copy(testLocation = Fixtures.RESTAURANT) }
        awaitTop(container, Fixtures.RESTAURANT_NAME)

        assertEquals(Fixtures.RESTAURANT, container.state.value.position)
    }

    @Test
    fun clearingTheTestLocationGoesBackToTheGps() {
        val container = Fixtures.driveContainer(location = Fixtures.CAFE)
        container.updateSettings { it.copy(testLocation = Fixtures.RESTAURANT) }
        awaitTop(container, Fixtures.RESTAURANT_NAME)

        container.updateSettings { it.copy(testLocation = null) }
        awaitTop(container, Fixtures.CAFE_NAME)

        assertEquals(Fixtures.CAFE, container.state.value.position)
    }

    @Test
    fun aLiveGpsFixIsReplacedByTheTestLocationWhileTheScreenIsOn() {
        // The GPS reports the cafe, but the test location is the restaurant. If the GPS fix were
        // used, the position would move back to the cafe once the screen is on.
        val fixes = MutableSharedFlow<LatLon>(replay = 1)
        val container = Fixtures.driveContainer(location = Fixtures.CAFE, live = fixes)
        container.updateSettings { it.copy(testLocation = Fixtures.RESTAURANT) }
        awaitTop(container, Fixtures.RESTAURANT_NAME)
        fixes.tryEmit(Fixtures.CAFE)

        container.setVisible(true)

        awaitPosition(container, Fixtures.RESTAURANT)
        assertEquals(Fixtures.RESTAURANT, container.state.value.position)
    }

    @Test
    fun nextThenPreviousThroughTheContainerRestoresThePlace() {
        val container = Fixtures.driveContainer(location = Fixtures.CAFE)
        container.refresh()
        awaitTop(container, Fixtures.CAFE_NAME)

        container.next()
        assertEquals(Fixtures.RESTAURANT_NAME, container.state.value.current?.poi?.name)

        container.previous()
        assertEquals(Fixtures.CAFE_NAME, container.state.value.current?.poi?.name)
    }

    private fun awaitTop(container: DriveContainer, name: String) =
        awaitUntil { container.state.value.current?.poi?.name == name }

    private fun awaitPosition(container: DriveContainer, position: LatLon) =
        awaitUntil { container.state.value.position == position }

    private fun awaitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (!condition() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
