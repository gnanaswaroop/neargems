package com.nearwise.domain

import kotlin.math.max
import kotlin.math.roundToInt

/** Short distance for the screen, e.g. "250 m" or "1.4 km". */
fun formatDistance(meters: Double): String =
    if (meters < 1000) "${(meters / 10).roundToInt() * 10} m" else kilometres(meters, "km")

/** Distance worded for text-to-speech, e.g. "250 meters" or "1.4 kilometers". */
fun spokenDistance(meters: Double): String =
    if (meters < 1000) "${(meters / 10).roundToInt() * 10} meters" else kilometres(meters, "kilometers")

/** The line read aloud for a suggestion. */
fun speechFor(ranked: RankedPoi): String = buildString {
    append(ranked.poi.name).append(". ")
    append(ranked.poi.category.label).append(", ")
    append(spokenDistance(ranked.distanceMeters)).append(" away. ")
    append(ranked.poi.snippet.replace(" · ", ", "))
}

/** Rough walking time at about 5 km/h. An estimate, not routing. */
fun walkMinutes(meters: Double): Int = max(1, (meters / WALK_METRES_PER_MIN).roundToInt())

/** Rough driving time at about 30 km/h in town. An estimate, not routing. */
fun driveMinutes(meters: Double): Int = max(1, (meters / DRIVE_METRES_PER_MIN).roundToInt())

private const val WALK_METRES_PER_MIN = 83.0
private const val DRIVE_METRES_PER_MIN = 500.0

private fun kilometres(meters: Double, unit: String): String {
    val tenths = (meters / 100).roundToInt()
    return "${tenths / 10}.${tenths % 10} $unit"
}
