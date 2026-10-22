package com.neargems.app

import com.neargems.platform.MemorySeenStore
import com.neargems.support.Fixtures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A place is New until the user has seen it, and the answer comes from the seen store, which on
 * Android is a SQLite database. These tests use the in-memory store, so what they check is when a
 * place is marked and that a new container reading the same store remembers it.
 */
class SeenPlacesTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun everyPlaceIsNewUntilItIsSeen() {
        val container = Fixtures.driveContainer()
        container.refresh()
        awaitUntil { container.state.value.current != null }

        assertTrue(container.seenIds.value.isEmpty())
    }

    @Test
    fun passingAPlaceWithNextMarksItSeen() {
        val container = Fixtures.driveContainer()
        container.refresh()
        awaitUntil { container.state.value.current?.poi?.name == Fixtures.CAFE_NAME }
        val cafe = container.state.value.current!!.poi

        container.next()

        assertTrue(cafe.id in container.seenIds.value)
        // The next place has not been seen yet.
        val restaurant = container.state.value.current!!.poi
        assertFalse(restaurant.id in container.seenIds.value)
    }

    @Test
    fun readingAPlaceAloudMarksItSeen() {
        val container = Fixtures.driveContainer()
        container.refresh()
        awaitUntil { container.state.value.current != null }
        val place = container.state.value.current!!.poi

        container.speakCurrent()

        assertTrue(place.id in container.seenIds.value)
    }

    @Test
    fun aNewContainerRemembersWhatWasSeenBefore() {
        val store = MemorySeenStore()
        val first = Fixtures.driveContainer(seen = store)
        first.refresh()
        awaitUntil { first.state.value.current != null }
        val place = first.state.value.current!!.poi
        first.markSeen(place)

        // The app restarts: a new container is built over the same store.
        val second = Fixtures.driveContainer(seen = store)

        assertEquals(setOf(place.id), second.seenIds.value)
    }

    @Test
    fun seeingAPlaceTwiceKeepsOneEntry() {
        val container = Fixtures.driveContainer()
        container.refresh()
        awaitUntil { container.state.value.current != null }
        val place = container.state.value.current!!.poi

        container.markSeen(place)
        container.markSeen(place)

        assertEquals(1, container.seenIds.value.size)
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
