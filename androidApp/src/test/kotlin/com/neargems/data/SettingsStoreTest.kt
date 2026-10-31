package com.neargems.data

import com.neargems.support.Fixtures
import com.neargems.support.MemoryStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsStoreTest {

    @Test
    fun noTestLocationIsUsedUntilOneIsChosen() {
        assertNull(SettingsStore(MemoryStore()) { 0L }.settings.value.testLocation)
    }

    @Test
    fun aTestLocationIsSavedAndReadBackAfterARestart() {
        val store = MemoryStore()
        SettingsStore(store) { 0L }.update { it.copy(testLocation = Fixtures.RESTAURANT) }

        assertEquals(Fixtures.RESTAURANT, SettingsStore(store) { 0L }.settings.value.testLocation)
    }

    @Test
    fun clearingTheTestLocationIsRememberedAfterARestart() {
        val store = MemoryStore()
        val settings = SettingsStore(store) { 0L }
        settings.update { it.copy(testLocation = Fixtures.RESTAURANT) }
        settings.update { it.copy(testLocation = null) }

        assertNull(SettingsStore(store) { 0L }.settings.value.testLocation)
    }

    @Test
    fun undismissBringsBackOnlyThatPlace() {
        val settings = SettingsStore(MemoryStore()) { 0L }
        settings.dismiss("cafe")
        settings.dismiss("luigi")

        settings.undismiss("cafe")

        assertEquals(setOf("luigi"), settings.dismissedIds())
    }

    @Test
    fun aDismissedPlaceComesBackAfterTwelveHours() {
        var now = 0L
        val settings = SettingsStore(MemoryStore()) { now }
        settings.dismiss("cafe")

        now = TWELVE_HOURS - 1
        assertEquals(setOf("cafe"), settings.dismissedIds())

        now = TWELVE_HOURS
        assertEquals(emptySet<String>(), settings.dismissedIds())
    }

    @Test
    fun audioIsNotMutedByDefault() {
        assertEquals(false, SettingsStore(MemoryStore()) { 0L }.settings.value.muteAudio)
    }

    @Test
    fun theMuteChoiceIsSavedAndReadBackAfterARestart() {
        val store = MemoryStore()
        SettingsStore(store) { 0L }.update { it.copy(muteAudio = true) }

        assertEquals(true, SettingsStore(store) { 0L }.settings.value.muteAudio)
    }

    @Test
    fun theChosenPlaceViewIsRememberedAfterARestart() {
        val store = MemoryStore()
        SettingsStore(store) { 0L }.update { it.copy(placeView = PlaceView.LIST) }

        assertEquals(PlaceView.LIST, SettingsStore(store) { 0L }.settings.value.placeView)
    }

    @Test
    fun cardsAreTheDefaultPlaceView() {
        assertEquals(PlaceView.CARDS, SettingsStore(MemoryStore()) { 0L }.settings.value.placeView)
    }

    private companion object {
        const val TWELVE_HOURS = 12 * 60 * 60 * 1000L
    }
}
