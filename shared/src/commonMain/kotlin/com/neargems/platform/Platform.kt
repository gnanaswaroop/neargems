package com.neargems.platform

import com.neargems.domain.LatLon
import com.neargems.domain.Poi
import kotlinx.coroutines.flow.Flow

/** Device position. Returns null when permission is missing or no fix is available. */
interface LocationSource {
    suspend fun current(): LatLon?

    /** Position fixes as the device moves. Cold: collecting starts updates, cancelling stops them. */
    fun updates(): Flow<LatLon>
}

/**
 * Which way the phone is facing, in degrees clockwise from north (0 = north). Works while walking
 * or driving, as long as the phone's own sensors are available. Cold: collecting starts the sensor.
 */
interface HeadingSource {
    fun headings(): Flow<Float>
}

/** Reads suggestions aloud. Each platform routes this to its own speech engine. */
interface Narrator {
    fun speak(text: String)
    fun stop()
}

/** Opens a place in the device's maps app, or a web page in the browser. */
interface MapOpener {
    fun open(poi: Poi)
    fun openUrl(url: String)
}

/** Small persistent key-value storage: SharedPreferences on Android, NSUserDefaults on iOS. */
interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String?)
}

/**
 * Remembers which places the user has seen, so each can be marked New or Seen. A place is in the
 * store once it has been seen. Android keeps this in a local SQLite database.
 */
interface SeenStore {
    fun seenIds(): Set<String>
    fun markSeen(id: String, name: String, category: String, atMillis: Long)
}

/** A [SeenStore] that forgets everything when the app closes. For tests, and platforms without a database. */
class MemorySeenStore : SeenStore {
    private val ids = mutableSetOf<String>()
    override fun seenIds(): Set<String> = ids.toSet()
    override fun markSeen(id: String, name: String, category: String, atMillis: Long) {
        ids += id
    }
}
