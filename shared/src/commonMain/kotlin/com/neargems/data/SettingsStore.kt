package com.neargems.data

import com.neargems.domain.LatLon
import com.neargems.domain.PoiCategory
import com.neargems.platform.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Which colour scheme to show. [SYSTEM] follows the device's light or dark setting. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** How the places are shown: one card at a time, or a list to pick from. */
enum class PlaceView { CARDS, LIST }

data class AppSettings(
    val categories: Set<PoiCategory> = PoiCategory.DEFAULT,
    /** Comma-separated words a place's name or description must contain. Empty means no filter. */
    val keywords: String = "",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val placeView: PlaceView = PlaceView.CARDS,
    val radiusMeters: Int = 1000,
    /** Read the top suggestion aloud when a car connects. */
    val announceOnCar: Boolean = true,
    /** Silence every spoken announcement, including when a car is connected. */
    val muteAudio: Boolean = false,
    /** Android only: keep a foreground service running so car connections are noticed in the background. */
    val watchInBackground: Boolean = false,
    /** The id of the spoken voice, from the device's installed voices. Null means the device default. */
    val voice: String? = null,
    /** Testing switch that pretends a car is connected. */
    val simulateCar: Boolean = false,
    /** Testing: a position to use instead of the GPS fix. Null means the real location. */
    val testLocation: LatLon? = null,
)

class SettingsStore(private val store: KeyValueStore, private val clock: () -> Long) {
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        store.putString(KEY_CATEGORIES, next.categories.joinToString(",") { it.name })
        store.putString(KEY_KEYWORDS, next.keywords)
        store.putString(KEY_RADIUS, next.radiusMeters.toString())
        store.putString(KEY_ANNOUNCE_CAR, next.announceOnCar.toString())
        store.putString(KEY_MUTE, next.muteAudio.toString())
        store.putString(KEY_WATCH, next.watchInBackground.toString())
        store.putString(KEY_THEME, next.themeMode.name)
        store.putString(KEY_PLACE_VIEW, next.placeView.name)
        store.putString(KEY_VOICE, next.voice)
        store.putString(KEY_TEST_LAT, next.testLocation?.lat?.toString())
        store.putString(KEY_TEST_LON, next.testLocation?.lon?.toString())
        _settings.value = next
    }

    /** Hides a suggestion for [DISMISS_TTL_MS]. */
    fun dismiss(id: String) {
        val now = clock()
        val live = liveDismissals(now) + (id to now)
        store.putString(KEY_DISMISSED, live.joinToString("\n") { "${it.first}|${it.second}" })
    }

    fun dismissedIds(): Set<String> = liveDismissals(clock()).map { it.first }.toSet()

    /** Shows a suggestion that was hidden with Next again. */
    fun undismiss(id: String) {
        val live = liveDismissals(clock()).filter { it.first != id }
        store.putString(KEY_DISMISSED, live.joinToString("\n") { "${it.first}|${it.second}" })
    }

    fun clearDismissed() = store.putString(KEY_DISMISSED, null)

    /** Hides a place for good, because the user has been there. Nothing about when is kept. */
    fun markVisited(id: String) {
        store.putString(KEY_VISITED, (visitedIds() + id).joinToString("\n"))
    }

    fun visitedIds(): Set<String> =
        store.getString(KEY_VISITED).orEmpty().lines().filter { it.isNotBlank() }.toSet()

    fun clearVisited() = store.putString(KEY_VISITED, null)

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
            keywords = store.getString(KEY_KEYWORDS).orEmpty(),
            radiusMeters = store.getString(KEY_RADIUS)?.toIntOrNull() ?: defaults.radiusMeters,
            announceOnCar = store.getString(KEY_ANNOUNCE_CAR)?.toBooleanStrictOrNull() ?: defaults.announceOnCar,
            muteAudio = store.getString(KEY_MUTE)?.toBooleanStrictOrNull() ?: defaults.muteAudio,
            watchInBackground = store.getString(KEY_WATCH)?.toBooleanStrictOrNull() ?: defaults.watchInBackground,
            // Not kept between launches: a pretend car left on would keep the app refreshing forever.
            simulateCar = defaults.simulateCar,
            themeMode = store.getString(KEY_THEME)?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
                ?: defaults.themeMode,
            placeView = store.getString(KEY_PLACE_VIEW)?.let { name -> PlaceView.entries.firstOrNull { it.name == name } }
                ?: defaults.placeView,
            voice = store.getString(KEY_VOICE),
            testLocation = readTestLocation(),
        )
    }

    private fun readTestLocation(): LatLon? {
        val lat = store.getString(KEY_TEST_LAT)?.toDoubleOrNull() ?: return null
        val lon = store.getString(KEY_TEST_LON)?.toDoubleOrNull() ?: return null
        return LatLon(lat, lon)
    }

    private companion object {
        const val DISMISS_TTL_MS = 12 * 60 * 60 * 1000L
        const val KEY_CATEGORIES = "categories"
        const val KEY_KEYWORDS = "keywords"
        const val KEY_RADIUS = "radius_m"
        const val KEY_ANNOUNCE_CAR = "announce_car"
        const val KEY_MUTE = "mute_audio"
        const val KEY_VISITED = "marked_visited"
        const val KEY_WATCH = "watch_in_background"
        const val KEY_DISMISSED = "dismissed"
        const val KEY_THEME = "theme_mode"
        const val KEY_PLACE_VIEW = "place_view"
        const val KEY_VOICE = "voice"
        const val KEY_TEST_LAT = "test_lat"
        const val KEY_TEST_LON = "test_lon"
    }
}
