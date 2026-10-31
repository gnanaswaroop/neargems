package com.neargems.platform

import com.neargems.domain.LatLon
import com.neargems.domain.Poi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

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

/** A voice the device can read with. [id] is what the app stores; [label] is shown to the user. */
data class SpeechVoice(val id: String, val label: String)

/**
 * The pull of gravity along the phone's own axes, as (x across the screen, y up its long side). It
 * tells portrait from landscape, and whether the phone is still.
 */
interface PostureSource {
    fun gravity(): Flow<Pair<Float, Float>>
}

/** Used where the phone has no gravity sensor. The posture then stays at its default. */
object NoPostureSource : PostureSource {
    override fun gravity(): Flow<Pair<Float, Float>> = emptyFlow()
}

/**
 * Notifications for the phone's notification shade. [liveUpdate] keeps the place on screen in the
 * shade while the app runs; [newPlace] makes a short, soft sound when a new place is found.
 */
interface PlaceNotifier {
    fun liveUpdate(title: String, detail: String)
    fun newPlace(title: String, detail: String)
    fun clear()
}

/** Used where notifications are not shown, such as tests. */
object NoPlaceNotifier : PlaceNotifier {
    override fun liveUpdate(title: String, detail: String) = Unit
    override fun newPlace(title: String, detail: String) = Unit
    override fun clear() = Unit
}

/** Reads suggestions aloud. Each platform routes this to its own speech engine. */
interface Narrator {
    fun speak(text: String)
    fun stop()

    /** The voices installed on the device. Empty until the speech engine is ready. */
    val voices: StateFlow<List<SpeechVoice>> get() = NoVoices

    /** Reads with the voice whose id is [id], or with the device's default voice when null. */
    fun useVoice(id: String?) = Unit
}

private val NoVoices: StateFlow<List<SpeechVoice>> = MutableStateFlow(emptyList())

/** Opens a place in the device's maps app, or a web page in the browser. */
interface MapOpener {
    fun open(poi: Poi)
    fun openUrl(url: String)
}

/** Small persistent key-value storage: SharedPreferences on Android. */
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
