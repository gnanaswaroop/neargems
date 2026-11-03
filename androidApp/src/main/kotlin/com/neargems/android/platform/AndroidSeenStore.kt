package com.neargems.android.platform

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.neargems.platform.SeenStore

/**
 * The places the user has seen, in a local SQLite database on this device. Nothing here is uploaded.
 * One row per place. [FIRST_SEEN] keeps when the place was first seen; [LAST_SEEN] moves forward each time it is
 * seen again, so the card can show when it was last seen.
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
                "last_seen INTEGER NOT NULL)",
        )
    }

    /** Version 1 had only [FIRST_SEEN]; existing places count as last seen when first seen. */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN $LAST_SEEN INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE $TABLE SET $LAST_SEEN = $FIRST_SEEN")
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

    override fun markSeen(id: String, name: String, category: String, atMillis: Long) {
        val db = writableDatabase
        val row = ContentValues().apply {
            put("id", id)
            put("name", name)
            put("category", category)
            put(FIRST_SEEN, atMillis)
            put(LAST_SEEN, atMillis)
        }
        val inserted = db.insertWithOnConflict(TABLE, null, row, SQLiteDatabase.CONFLICT_IGNORE)
        if (inserted == -1L) {
            db.update(TABLE, ContentValues().apply { put(LAST_SEEN, atMillis) }, "id = ?", arrayOf(id))
        }
    }

    private companion object {
        const val DATABASE_NAME = "neargems.db"
        const val VERSION = 2
        const val TABLE = "seen_places"
        const val FIRST_SEEN = "first_seen"
        const val LAST_SEEN = "last_seen"
    }
}
