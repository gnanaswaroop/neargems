package com.neargems.domain

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** Where the sun is in the sky, as seen from one place: [azimuthDegrees] clockwise from north, [elevationDegrees] above the horizon. */
data class SunPosition(val azimuthDegrees: Double, val elevationDegrees: Double) {
    val isAboveHorizon: Boolean get() = elevationDegrees > 0
}

/**
 * Sun position for [observer] at [epochMillis], from the standard low-precision solar formulas. It is
 * accurate to a fraction of a degree, which is plenty for a compass marker. Refraction is ignored.
 */
fun sunPosition(observer: LatLon, epochMillis: Long): SunPosition {
    // Days since 2000-01-01 12:00 UT.
    val days = epochMillis / MILLIS_PER_DAY + JULIAN_DAY_UNIX_EPOCH - J2000
    val meanAnomaly = radians(357.528 + 0.9856003 * days)
    val eclipticLongitude = radians(280.460 + 0.9856474 * days) +
        radians(1.915) * sin(meanAnomaly) + radians(0.020) * sin(2 * meanAnomaly)
    val obliquity = radians(23.439 - 0.0000004 * days)
    val rightAscension = atan2(cos(obliquity) * sin(eclipticLongitude), cos(eclipticLongitude))
    val declination = asin(sin(obliquity) * sin(eclipticLongitude))

    // Local hour angle: how far the sun is past due south, in radians.
    val siderealDegrees = 280.46061837 + 360.98564736629 * days + observer.lon
    val hourAngle = radians(siderealDegrees) - rightAscension

    val latitude = radians(observer.lat)
    val elevation = asin(sin(latitude) * sin(declination) + cos(latitude) * cos(declination) * cos(hourAngle))
    // The formula measures from south, towards west. Turn it into a bearing from north, clockwise.
    val fromSouth = atan2(sin(hourAngle), cos(hourAngle) * sin(latitude) - tan(declination) * cos(latitude))
    val azimuth = (degrees(fromSouth) + 180 + 360) % 360

    return SunPosition(azimuthDegrees = azimuth, elevationDegrees = degrees(elevation))
}

private const val MILLIS_PER_DAY = 86_400_000.0
private const val JULIAN_DAY_UNIX_EPOCH = 2440587.5
private const val J2000 = 2451545.0

private fun radians(degrees: Double): Double = degrees * PI / 180

private fun degrees(radians: Double): Double = radians * 180 / PI
