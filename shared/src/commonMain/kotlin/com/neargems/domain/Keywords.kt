package com.neargems.domain

/** Splits what the user typed into keywords: comma-separated, trimmed, lower-case, without repeats. */
fun parseKeywords(text: String): List<String> =
    text.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()

/** True when the place's name or short text contains any keyword. No keywords match every place. */
fun Poi.matchesKeywords(keywords: List<String>): Boolean {
    if (keywords.isEmpty()) return true
    val name = name.lowercase()
    val snippet = snippet.lowercase()
    return keywords.any { keyword -> keyword in name || keyword in snippet }
}

private val TRANSPORT_WORDS = listOf("station", "railway", "tram stop", "bus stop", "halt")

/** Stations and stops are not places to visit, so they are never suggested as landmarks. */
fun Poi.isTransportStop(): Boolean {
    if (category != PoiCategory.LANDMARK) return false
    val name = name.lowercase()
    return TRANSPORT_WORDS.any { it in name }
}
