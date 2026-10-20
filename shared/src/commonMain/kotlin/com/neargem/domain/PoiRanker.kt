package com.neargem.domain

object PoiRanker {
    /** Points a place loses when it sits at the edge of the search radius. */
    const val MAX_DISTANCE_PENALTY = 35.0

    /**
     * Ranks places inside [radiusMeters] of [from], most interesting first.
     * Distance reduces interest, so a nearby good place can beat a far-away great one.
     */
    fun rank(
        candidates: List<Poi>,
        from: LatLon,
        radiusMeters: Int,
        excludedIds: Set<String>,
    ): List<RankedPoi> =
        candidates
            .asSequence()
            .filter { it.id !in excludedIds }
            .map { poi -> poi to distanceMeters(from, poi.location) }
            .filter { (_, distance) -> distance <= radiusMeters }
            .map { (poi, distance) ->
                RankedPoi(poi, distance, poi.interest - MAX_DISTANCE_PENALTY * (distance / radiusMeters))
            }
            .sortedWith(compareByDescending<RankedPoi> { it.score }.thenBy { it.distanceMeters })
            .toList()
}
