package com.neargems.domain

/** The four compass points shown around the direction pointer, with their bearing from north, clockwise. */
enum class CompassPoint(val letter: String, val bearing: Float) {
    NORTH("N", 0f),
    EAST("E", 90f),
    SOUTH("S", 180f),
    WEST("W", 270f),
}

/** Wraps an angle in degrees into the range [0, 360). */
fun normalizeDegrees(degrees: Float): Float = ((degrees % 360f) + 360f) % 360f

/**
 * Where a compass point sits on the pointer, in degrees clockwise from the top. The pointer's top is the
 * phone's heading, so a point at bearing 0 (north) is at minus the heading.
 */
fun pointerAngle(point: CompassPoint, headingDegrees: Float): Float = normalizeDegrees(point.bearing - headingDegrees)
