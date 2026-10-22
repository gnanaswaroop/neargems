package com.neargems.android.platform

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.neargems.platform.SeenStore

/**
 * The places the user has seen, in a local SQLite database on this device. Nothing here is uploaded.
 * One row per place; seeing a place again leaves its first-seen time as it was.
 */
class AndroidSeenStore(context: Context, name: String? = DATABASE_NAME) :
    SQLiteOpenHelper(context.applicationContext, name, null, VERSION), SeenStore {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE $TABLE (" +
                "id TEXT PRIMARY KEY NOT NULL, " +
                "name TEXT NOT NULL, " +
                "category TEXT NOT NULL, " +
                "first_seen INTEGER NOT NULL)",
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    override fun seenIds(): Set<String> =
        readableDatabase.rawQuery("SELECT id FROM $TABLE", null).use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }

    override fun markSeen(id: String, name: String, category: String, atMillis: Long) {
        val row = ContentValues().apply {
            put("id", id)
            put("name", name)
            put("category", category)
            put("first_seen", atMillis)
        }
        writableDatabase.insertWithOnConflict(TABLE, null, row, SQLiteDatabase.CONFLICT_IGNORE)
    }

    private companion object {
        const val DATABASE_NAME = "neargems.db"
        const val VERSION = 1
        const val TABLE = "seen_places"
    }
}
