package com.drivescout.data

import com.drivescout.domain.PoiCategory
import com.drivescout.platform.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val categories: Set<PoiCategory> = PoiCategory.DEFAULT,
    val radiusMeters: Int = 1000,
    /** Read the top suggestion aloud when a car connects. */
    val announceOnCar: Boolean = true,
    /** Android only: keep a foreground service running so car connections are noticed in the background. */
    val watchInBackground: Boolean = false,
    /** Testing switch that pretends a car is connected. */
    val simulateCar: Boolean = false,
)

class SettingsStore(private val store: KeyValueStore, private val clock: () -> Long) {
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        store.putString(KEY_CATEGORIES, next.categories.joinToString(",") { it.name })
        store.putString(KEY_RADIUS, next.radiusMeters.toString())
        store.putString(KEY_ANNOUNCE_CAR, next.announceOnCar.toString())
        store.putString(KEY_WATCH, next.watchInBackground.toString())
        store.putString(KEY_SIMULATE, next.simulateCar.toString())
        _settings.value = next
    }

    /** Hides a suggestion for [DISMISS_TTL_MS]. */
    fun dismiss(id: String) {
        val now = clock()
        val live = liveDismissals(now) + (id to now)
        store.putString(KEY_DISMISSED, live.joinToString("\n") { "${it.first}|${it.second}" })
    }

    fun dismissedIds(): Set<String> = liveDismissals(clock()).map { it.first }.toSet()

    fun clearDismissed() = store.putString(KEY_DISMISSED, null)

    private fun liveDismissals(now: Long): List<Pair<String, Long>> =
        store.getString(KEY_DISMISSED).orEmpty()
            .lines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val timestamp = line.substringAfter('|').toLongOrNull() ?: return@mapNotNull null
                line.substringBefore('|') to timestamp
            }
            .filter { now - it.second < DISMISS_TTL_MS }

    private fun read(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            categories = store.getString(KEY_CATEGORIES)?.let { raw ->
                raw.split(',')
                    .filter { it.isNotBlank() }
                    .mapNotNull { name -> PoiCategory.entries.firstOrNull { it.name == name } }
                    .toSet()
            } ?: defaults.categories,
            radiusMeters = store.getString(KEY_RADIUS)?.toIntOrNull() ?: defaults.radiusMeters,
            announceOnCar = store.getString(KEY_ANNOUNCE_CAR)?.toBooleanStrictOrNull() ?: defaults.announceOnCar,
            watchInBackground = store.getString(KEY_WATCH)?.toBooleanStrictOrNull() ?: defaults.watchInBackground,
            simulateCar = store.getString(KEY_SIMULATE)?.toBooleanStrictOrNull() ?: defaults.simulateCar,
        )
    }

    private companion object {
        const val DISMISS_TTL_MS = 12 * 60 * 60 * 1000L
        const val KEY_CATEGORIES = "categories"
        const val KEY_RADIUS = "radius_m"
        const val KEY_ANNOUNCE_CAR = "announce_car"
        const val KEY_WATCH = "watch_in_background"
        const val KEY_SIMULATE = "simulate_car"
        const val KEY_DISMISSED = "dismissed"
    }
}
