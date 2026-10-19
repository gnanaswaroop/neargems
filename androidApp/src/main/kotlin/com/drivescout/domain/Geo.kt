package com.drivescout.domain

import kotlin.math.PI
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

private const val EARTH_RADIUS_M = 6_371_000.0
