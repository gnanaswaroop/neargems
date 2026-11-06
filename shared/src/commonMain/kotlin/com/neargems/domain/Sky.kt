package com.neargems.domain

/** The two objects the direction pointer can show. */
enum class SkyBody { SUN, MOON }

/** The object the direction pointer shows, and where it is. [SunPosition] is the same shape for both. */
data class SkyMarker(val body: SkyBody, val position: SunPosition)

/**
 * The object for the direction pointer: the sun while it is up; otherwise the moon, so there is always one object
 * on the pointer. If the moon is also down, it is still shown, dimmed. Null only before the first position fix.
 */
fun pointerSky(sun: SunPosition?, moon: SunPosition?): SkyMarker? = when {
    sun != null && sun.isAboveHorizon -> SkyMarker(SkyBody.SUN, sun)
    moon != null -> SkyMarker(SkyBody.MOON, moon)
    sun != null -> SkyMarker(SkyBody.SUN, sun)
    else -> null
}
