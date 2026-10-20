package com.neargems.data

import com.neargems.platform.KeyValueStore
import org.junit.Assert.assertEquals
import org.junit.Test

class VisitStoreTest {

    private class MemoryStore : KeyValueStore {
        val values = mutableMapOf<String, String>()
        override fun getString(key: String): String? = values[key]
        override fun putString(key: String, value: String?) {
            if (value == null) values.remove(key) else values[key] = value
        }
    }

    @Test
    fun aVisitIsRecordedOnceWithinTwelveHours() {
        var now = 1_000L
        val store = MemoryStore()
        val visits = VisitStore(store) { now }

        visits.record("osm:node:1", "Corner Cafe", "Café")
        now += 60_000
        visits.record("osm:node:1", "Corner Cafe", "Café") // same place, a minute later: not a new visit

        assertEquals(1, visits.visits.value.size)

        now += VisitStore.DEDUPE_MS
        visits.record("osm:node:1", "Corner Cafe", "Café") // after the window it counts again

        assertEquals(2, visits.visits.value.size)
        assertEquals(now, visits.visits.value.first().atMillis)
    }

    @Test
    fun visitsSurviveARestart() {
        val store = MemoryStore()
        VisitStore(store) { 5L }.record("wiki:9", "Nelson's Column", "Landmark")

        val reloaded = VisitStore(store) { 6L }

        assertEquals(listOf(Visit("wiki:9", "Nelson's Column", "Landmark", 5L)), reloaded.visits.value)
    }

    @Test
    fun separatorsInNamesDoNotBreakTheStoredLines() {
        val store = MemoryStore()
        VisitStore(store) { 1L }.record("wiki:2", "Bread | Butter\nShop", "Café")

        val reloaded = VisitStore(store) { 2L }

        assertEquals("Bread   Butter Shop", reloaded.visits.value.single().name)
    }
}
