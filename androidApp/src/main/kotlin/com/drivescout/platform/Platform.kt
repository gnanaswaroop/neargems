package com.drivescout.platform

import com.drivescout.domain.LatLon
import com.drivescout.domain.Poi

/** Device position. Returns null when permission is missing or no fix is available. */
interface LocationSource {
    suspend fun current(): LatLon?
}

/** Reads suggestions aloud. Each platform routes this to its own speech engine. */
interface Narrator {
    fun speak(text: String)
    fun stop()
}

/** Opens a place in the device's maps app. */
interface MapOpener {
    fun open(poi: Poi)
}

/** Small persistent key-value storage: SharedPreferences on Android, NSUserDefaults on iOS. */
interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String?)
}
