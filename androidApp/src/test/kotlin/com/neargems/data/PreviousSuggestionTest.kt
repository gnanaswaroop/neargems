package com.neargems.data

import com.neargems.domain.LatLon
import com.neargems.support.Fixtures
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Next hides a place and Back brings the most recent one back, in the order they were passed. */
class PreviousSuggestionTest {

    @Test
    fun nextThenPreviousBringsTheSamePlaceBack() = runBlocking {
        val repository = Fixtures.repository()
        repository.refresh(announce = false)
        assertFalse(repository.state.value.hasPrevious)

        repository.next(announce = false)
        assertEquals(Fixtures.RESTAURANT_NAME, repository.state.value.current?.poi?.name)
        assertTrue(repository.state.value.hasPrevious)
        assertEquals(Fixtures.CAFE_NAME, repository.state.value.previous?.poi?.name)

        repository.previous(announce = false)
        assertEquals(Fixtures.CAFE_NAME, repository.state.value.current?.poi?.name)
        assertFalse(repository.state.value.hasPrevious)
        assertNull(repository.state.value.previous)
    }

    @Test
    fun previousWalksBackThroughSeveralPlacesInOrder() = runBlocking {
        val repository = Fixtures.repository()
        repository.refresh(announce = false)

        repository.next(announce = false) // passes the cafe
        repository.next(announce = false) // passes Luigi, nothing left
        assertNull(repository.state.value.current)

        repository.previous(announce = false)
        assertEquals(Fixtures.RESTAURANT_NAME, repository.state.value.current?.poi?.name)

        repository.previous(announce = false)
        assertEquals(Fixtures.CAFE_NAME, repository.state.value.current?.poi?.name)
        assertFalse(repository.state.value.hasPrevious)
    }

    @Test
    fun theLeftCardStaysAvailableAfterMovingPastTheSearchRadius() = runBlocking {
        val repository = Fixtures.repository()
        repository.refresh(announce = false)
        repository.next(announce = false) // passes the cafe

        // About 5 km north, well outside the 1 km search: nothing is nearby, but Back must still work.
        repository.onPosition(LatLon(Fixtures.RESTAURANT.lat + 0.05, Fixtures.RESTAURANT.lon), announce = false)

        assertTrue(repository.state.value.hasPrevious)
        assertEquals(Fixtures.CAFE_NAME, repository.state.value.previous?.poi?.name)
    }

    @Test
    fun previousWithNothingPassedChangesNothing() = runBlocking {
        val repository = Fixtures.repository()
        repository.refresh(announce = false)

        repository.previous(announce = false)

        assertEquals(Fixtures.CAFE_NAME, repository.state.value.current?.poi?.name)
        assertFalse(repository.state.value.hasPrevious)
    }
}
