package com.drivescout.domain

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

private fun kilometres(meters: Double, unit: String): String {
    val tenths = (meters / 100).roundToInt()
    return "${tenths / 10}.${tenths % 10} $unit"
}
