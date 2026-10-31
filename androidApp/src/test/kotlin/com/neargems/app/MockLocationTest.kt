package com.neargems.app

import com.neargems.domain.LatLon
import com.neargems.support.FixedLocation
import com.neargems.support.Fixtures
import com.neargems.support.MemoryStore
import com.neargems.support.NoHeadings
import com.neargems.support.NoMaps
import com.neargems.support.SilentNarrator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** Choosing a mock location moves the suggestions to it: the state's position follows the choice. */
class MockLocationTest {

    private val trafalgar = LatLon(51.5080, -0.1281)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun choosingAMockLocationMovesThePositionTheSuggestionsUse() {
        val container = DriveContainer(
            PlatformServices(
                http = Fixtures.httpClient(),
                keyValues = MemoryStore(),
                location = FixedLocation(Fixtures.CAFE, emptyFlow()),
                headings = NoHeadings(),
                narrator = SilentNarrator(),
                maps = NoMaps(),
                carConnected = flowOf(false),
                language = "en",
                clock = { 0L },
            ),
        )
        container.refresh()
        awaitUntil { container.state.value.position == Fixtures.CAFE }

        container.updateSettings { it.copy(testLocation = trafalgar) }
        awaitUntil { container.state.value.position == trafalgar }

        assertEquals(trafalgar, container.state.value.position)
    }

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
