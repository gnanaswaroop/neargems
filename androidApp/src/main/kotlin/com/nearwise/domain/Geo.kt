package com.nearwise.domain

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLon(val lat: Double, val lon: Double)

/** Great-circle distance in metres (haversine). */
fun distanceMeters(a: LatLon, b: LatLon): Double {
    val dLat = (b.lat - a.lat) * PI / 180
    val dLon = (b.lon - a.lon) * PI / 180
    val h = sin(dLat / 2) * sin(dLat / 2) +
        cos(a.lat * PI / 180) * cos(b.lat * PI / 180) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * EARTH_RADIUS_M * asin(sqrt(h))
}

/** Initial compass bearing in degrees (0 = north, clockwise) from [from] towards [to]. */
fun bearingDegrees(from: LatLon, to: LatLon): Double {
    val lat1 = from.lat * PI / 180
    val lat2 = to.lat * PI / 180
    val dLon = (to.lon - from.lon) * PI / 180
    val y = sin(dLon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
    return (atan2(y, x) * 180 / PI + 360) % 360
}

/** The nearest of the eight compass points for [degrees], e.g. "NE". */
fun compassPoint(degrees: Double): String =
    listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[((degrees + 22.5) / 45).toInt() % 8]

private const val EARTH_RADIUS_M = 6_371_000.0
