package com.neargems.android.platform

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.neargems.domain.LatLon
import com.neargems.platform.SeenPlace
import com.neargems.platform.SeenStore

/**
 * The places the user has seen, in a local SQLite database on this device. Nothing here is uploaded.
 * One row per place. [FIRST_SEEN] keeps when the place was first seen; [LAST_SEEN] moves forward each time it is
 * seen again, so the card can show when it was last seen. [LAT] and [LON] keep where the place is, so the places
 * can be opened from Settings even when they are far away.
 */
class AndroidSeenStore(context: Context, name: String? = DATABASE_NAME) :
    SQLiteOpenHelper(context.applicationContext, name, null, VERSION), SeenStore {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE $TABLE (" +
                "id TEXT PRIMARY KEY NOT NULL, " +
                "name TEXT NOT NULL, " +
                "category TEXT NOT NULL, " +
                "first_seen INTEGER NOT NULL, " +
                "last_seen INTEGER NOT NULL, " +
                "$LAT REAL, " +
                "$LON REAL)",
        )
    }

    /**
     * Version 1 had only [FIRST_SEEN]; existing places count as last seen when first seen.
     * Version 2 added [LAST_SEEN]; places seen before version 3 have no location until they are seen again.
     */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN $LAST_SEEN INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE $TABLE SET $LAST_SEEN = $FIRST_SEEN")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN $LAT REAL")
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN $LON REAL")
        }
    }

    override fun seenIds(): Set<String> =
        readableDatabase.rawQuery("SELECT id FROM $TABLE", null).use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }

    override fun lastSeenTimes(): Map<String, Long> =
        readableDatabase.rawQuery("SELECT id, $LAST_SEEN FROM $TABLE", null).use { cursor ->
            buildMap {
                while (cursor.moveToNext()) put(cursor.getString(0), cursor.getLong(1))
            }
        }

    override fun seenPlaces(): List<SeenPlace> =
        readableDatabase.rawQuery(
            "SELECT id, name, category, $FIRST_SEEN, $LAST_SEEN, $LAT, $LON FROM $TABLE ORDER BY $LAST_SEEN DESC",
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val location = if (cursor.isNull(5) || cursor.isNull(6)) null else LatLon(cursor.getDouble(5), cursor.getDouble(6))
                    add(SeenPlace(cursor.getString(0), cursor.getString(1), cursor.getString(2), location, cursor.getLong(3), cursor.getLong(4)))
                }
            }
        }

    override fun markSeen(id: String, name: String, category: String, atMillis: Long, location: LatLon?) {
        val db = writableDatabase
        val row = ContentValues().apply {
            put("id", id)
            put("name", name)
            put("category", category)
            put(FIRST_SEEN, atMillis)
            put(LAST_SEEN, atMillis)
            location?.let {
                put(LAT, it.lat)
                put(LON, it.lon)
            }
        }
        val inserted = db.insertWithOnConflict(TABLE, null, row, SQLiteDatabase.CONFLICT_IGNORE)
        if (inserted == -1L) {
            val update = ContentValues().apply {
                put(LAST_SEEN, atMillis)
                location?.let {
                    put(LAT, it.lat)
                    put(LON, it.lon)
                }
            }
            db.update(TABLE, update, "id = ?", arrayOf(id))
        }
    }

    private companion object {
        const val DATABASE_NAME = "neargems.db"
        const val VERSION = 3
        const val TABLE = "seen_places"
        const val FIRST_SEEN = "first_seen"
        const val LAST_SEEN = "last_seen"
        const val LAT = "lat"
        const val LON = "lon"
    }
}
