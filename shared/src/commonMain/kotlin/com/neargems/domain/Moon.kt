package com.neargems.domain

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Moon position for [observer] at [epochMillis], from the low-precision lunar formulas (the main terms of the
 * longitude, latitude and distance series). It is accurate to about a degree, which is plenty for a compass marker.
 * The result has the same shape as the sun's: azimuth clockwise from north, elevation above the horizon.
 */
fun moonPosition(observer: LatLon, epochMillis: Long): SunPosition {
    // Days since 2000-01-01 12:00 UT.
    val days = epochMillis / MILLIS_PER_DAY + JULIAN_DAY_UNIX_EPOCH - J2000
    // Mean longitude, mean anomaly and argument of latitude of the moon, and the mean elongation from the sun.
    val meanLongitude = 218.316 + 13.176396 * days
    val meanAnomaly = radians(134.963 + 13.064993 * days)
    val argumentOfLatitude = radians(93.272 + 13.229350 * days)
    val elongation = radians(297.850 + 12.190749 * days)
    val sunAnomaly = radians(357.529 + 0.98560028 * days)

    val eclipticLongitude = radians(meanLongitude) +
        radians(6.289) * sin(meanAnomaly) +
        radians(1.274) * sin(2 * elongation - meanAnomaly) +
        radians(0.658) * sin(2 * elongation) +
        radians(0.214) * sin(2 * meanAnomaly) -
        radians(0.186) * sin(sunAnomaly) -
        radians(0.114) * sin(2 * argumentOfLatitude)
    val eclipticLatitude = radians(5.128) * sin(argumentOfLatitude) +
        radians(0.281) * sin(meanAnomaly + argumentOfLatitude) +
        radians(0.278) * sin(meanAnomaly - argumentOfLatitude)

    // Ecliptic to equatorial coordinates, using the obliquity of the ecliptic.
    val obliquity = radians(23.439 - 0.0000004 * days)
    val rightAscension = atan2(
        sin(eclipticLongitude) * cos(obliquity) - kotlin.math.tan(eclipticLatitude) * sin(obliquity),
        cos(eclipticLongitude),
    )
    val declination = asin(
        sin(eclipticLatitude) * cos(obliquity) + cos(eclipticLatitude) * sin(obliquity) * sin(eclipticLongitude),
    )
    return horizontalPosition(observer, days, rightAscension, declination)
}
