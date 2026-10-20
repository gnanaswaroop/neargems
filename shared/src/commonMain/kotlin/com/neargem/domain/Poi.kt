package com.neargem.domain

/** A kind of place the user wants suggestions for. */
enum class PoiCategory(val label: String, val osmAmenities: List<String>) {
    RESTAURANT("Restaurant", listOf("restaurant")),
    CAFE("Café", listOf("cafe")),
    FAST_FOOD("Fast food", listOf("fast_food")),
    BAR("Bar or pub", listOf("bar", "pub")),

    /** Sourced from Wikipedia articles rather than OpenStreetMap tags. */
    LANDMARK("Landmark", emptyList());

    companion object {
        val DEFAULT: Set<PoiCategory> = setOf(RESTAURANT, CAFE, LANDMARK)
    }
}

data class Poi(
    val id: String,
    val name: String,
    val category: PoiCategory,
    val location: LatLon,
    /** Short read-out text: cuisine and hours, or the start of a Wikipedia article. */
    val snippet: String,
    /** How interesting the place is on its own, before distance is considered. */
    val interest: Double,
    /** A picture from Wikipedia, loaded at runtime from its public URL. Not bundled with the app. */
    val photo: Photo? = null,
    /** The Wikipedia article for the place, for "read more". */
    val link: String? = null,
)

/** [imageUrl] is the thumbnail; [fileUrl] is the Wikipedia file page that lists its licence. */
data class Photo(val imageUrl: String, val fileUrl: String)

data class RankedPoi(val poi: Poi, val distanceMeters: Double, val score: Double)
