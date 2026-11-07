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
    fun theLastSeenTimeMovesForwardWhenAPlaceIsSeenAgain() {
        val first = AndroidSeenStore(context, databaseName)
        first.markSeen("osm:node:1", "Corner Cafe", "Café", 1_000L)
        first.markSeen("osm:node:1", "Corner Cafe", "Café", 5_000L)
        assertEquals(5_000L, first.lastSeenTimes()["osm:node:1"])
        first.close()

        val reopened = AndroidSeenStore(context, databaseName)
        assertEquals(5_000L, reopened.lastSeenTimes()["osm:node:1"])
        reopened.close()
    }

    @Test
    fun aVersionOneDatabaseIsUpgradedKeepingItsPlacesWithTheirFirstSeenTime() {
        // A database as version 1 wrote it: no last_seen column, one place already seen.
        val old = context.openOrCreateDatabase(databaseName, android.content.Context.MODE_PRIVATE, null)
        old.execSQL(
            "CREATE TABLE seen_places (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, " +
                "category TEXT NOT NULL, first_seen INTEGER NOT NULL)",
        )
        old.execSQL("INSERT INTO seen_places VALUES ('osm:node:1', 'Corner Cafe', 'Café', 1000)")
        old.version = 1
        old.close()

        // Opening it with the current store upgrades it to version 2.
        val upgraded = AndroidSeenStore(context, databaseName)
        assertTrue(upgraded.seenIds().contains("osm:node:1"))
        assertEquals(1_000L, upgraded.lastSeenTimes()["osm:node:1"])

        // A place seen after the upgrade gets its own last-seen time.
        upgraded.markSeen("osm:node:1", "Corner Cafe", "Café", 9_000L)
        assertEquals(9_000L, upgraded.lastSeenTimes()["osm:node:1"])
        upgraded.close()
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

    @Test
    fun aVersionTwoDatabaseIsUpgradedKeepingItsPlacesWithoutALocationUntilTheyAreSeenAgain() {
        // A database as version 2 wrote it: last_seen but no location.
        val old = context.openOrCreateDatabase(databaseName, android.content.Context.MODE_PRIVATE, null)
        old.execSQL(
            "CREATE TABLE seen_places (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, " +
                "category TEXT NOT NULL, first_seen INTEGER NOT NULL, last_seen INTEGER NOT NULL)",
        )
        old.execSQL("INSERT INTO seen_places VALUES ('osm:node:1', 'Corner Cafe', 'Café', 1000, 4000)")
        old.version = 2
        old.close()

        val upgraded = AndroidSeenStore(context, databaseName)
        val kept = upgraded.seenPlaces().single()
        assertEquals("osm:node:1", kept.id)
        assertEquals(4_000L, kept.lastSeenMillis)
        assertEquals(null, kept.location)

        // Seen again after the upgrade, the place gets its location.
        upgraded.markSeen("osm:node:1", "Corner Cafe", "Café", 9_000L, com.neargems.domain.LatLon(51.5, -0.12))
        val located = upgraded.seenPlaces().single()
        assertEquals(com.neargems.domain.LatLon(51.5, -0.12), located.location)
        assertEquals(1_000L, located.firstSeenMillis)
        upgraded.close()
    }
}
