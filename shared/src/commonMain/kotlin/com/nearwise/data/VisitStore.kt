package com.nearwise.data

import com.nearwise.platform.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A place the user has been close to. */
data class Visit(val poiId: String, val name: String, val categoryLabel: String, val atMillis: Long)

/**
 * The on-device history of visited places. It is stored only on this device and is never sent
 * anywhere. Each place is recorded once per [DEDUPE_MS], and only the latest [MAX_VISITS] are kept.
 */
class VisitStore(private val store: KeyValueStore, private val clock: () -> Long) {
    private val _visits = MutableStateFlow(read())

    /** Newest first. */
    val visits: StateFlow<List<Visit>> = _visits.asStateFlow()

    /** Records a visit unless this place was already recorded within [DEDUPE_MS]. */
    fun record(poiId: String, name: String, categoryLabel: String) {
        val now = clock()
        val current = _visits.value
        if (current.any { it.poiId == poiId && now - it.atMillis < DEDUPE_MS }) return
        val next = (listOf(Visit(poiId, clean(name), clean(categoryLabel), now)) + current).take(MAX_VISITS)
        write(next)
        _visits.value = next
    }

    private fun write(list: List<Visit>) {
        store.putString(
            KEY,
            list.joinToString("\n") { listOf(it.poiId, it.name, it.categoryLabel, it.atMillis).joinToString("|") },
        )
    }

    private fun read(): List<Visit> =
        store.getString(KEY).orEmpty().lines().mapNotNull { line ->
            val parts = line.split('|')
            if (parts.size != 4) return@mapNotNull null
            Visit(parts[0], parts[1], parts[2], parts[3].toLongOrNull() ?: return@mapNotNull null)
        }

    /** Keeps the stored line format intact: the separator and line breaks are not allowed in names. */
    private fun clean(text: String): String = text.replace('|', ' ').replace('\n', ' ')

    companion object {
        const val DEDUPE_MS = 12 * 60 * 60 * 1000L
        const val MAX_VISITS = 200
        const val KEY = "visits"
    }
}
