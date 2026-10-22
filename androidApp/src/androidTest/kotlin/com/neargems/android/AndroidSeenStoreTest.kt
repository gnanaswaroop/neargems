package com.neargems.android

import androidx.test.platform.app.InstrumentationRegistry
import com.neargems.android.platform.AndroidSeenStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The seen places live in a real SQLite database on the device, and survive the store being reopened. */
class AndroidSeenStoreTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "seen-store-test.db"

    @Before
    fun setUp() {
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun startsEmpty() {
        val store = AndroidSeenStore(context, databaseName)
        assertTrue(store.seenIds().isEmpty())
        store.close()
    }

    @Test
    fun aSeenPlaceIsStillThereAfterTheDatabaseIsReopened() {
        val first = AndroidSeenStore(context, databaseName)
        first.markSeen("osm:node:1", "Corner Cafe", "Café", 1_000L)
        first.markSeen("osm:node:2", "Luigi", "Restaurant", 2_000L)
        first.close()

        val reopened = AndroidSeenStore(context, databaseName)
        assertEquals(setOf("osm:node:1", "osm:node:2"), reopened.seenIds())
        reopened.close()
    }

    @Test
    fun seeingAPlaceTwiceKeepsOneRowAndTheFirstSeenTime() {
        val store = AndroidSeenStore(context, databaseName)
        store.markSeen("osm:node:1", "Corner Cafe", "Café", 1_000L)
        store.markSeen("osm:node:1", "Corner Cafe", "Café", 9_000L)

        assertEquals(setOf("osm:node:1"), store.seenIds())
        val firstSeen = store.readableDatabase
            .rawQuery("SELECT first_seen FROM seen_places WHERE id = ?", arrayOf("osm:node:1"))
            .use { cursor ->
                cursor.moveToFirst()
                cursor.getLong(0)
            }
        assertEquals(1_000L, firstSeen)
        store.close()
    }
}
