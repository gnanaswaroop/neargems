package com.nearwise.data

import com.nearwise.domain.LatLon
import com.nearwise.domain.PoiCategory
import com.nearwise.domain.PoiRanker
import com.nearwise.domain.RankedPoi
import com.nearwise.domain.distanceMeters
import com.nearwise.domain.speechFor
import com.nearwise.platform.LocationSource
import com.nearwise.platform.Narrator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class PoiState(
    val loading: Boolean = false,
    /** The suggestion to show now, or null when none are left nearby. */
    val current: RankedPoi? = null,
    /** How many more suggestions are queued after [current]. */
    val queued: Int = 0,
    /** The queued suggestions after [current], best first. */
    val upcoming: List<RankedPoi> = emptyList(),
    /** Shown when there is no suggestion, e.g. why nothing could be loaded. */
    val message: String? = null,
    /** Where the device was at the last fix, so the UI can show live direction and time. */
    val position: LatLon? = null,
)

class PoiRepository(
    private val settings: SettingsStore,
    private val finder: PoiFinder,
    private val location: LocationSource,
    private val narrator: Narrator,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(PoiState(message = "Finding places nearby…"))
    val state: StateFlow<PoiState> = _state.asStateFlow()

    private var here: LatLon? = null
    private var fetchedFrom: LatLon? = null
    private var fetchedFor: FetchKey? = null
    private var candidates: List<com.nearwise.domain.Poi> = emptyList()
    private var announcedId: String? = null

    /**
     * Re-reads the device location. Places are only re-queried after moving [REFETCH_METERS]
     * or changing categories or radius; otherwise the cached candidates are re-ranked.
     */
    suspend fun refresh(announce: Boolean) {
        mutex.withLock {
            _state.update { it.copy(loading = true) }
            val position = location.current()
            if (position == null) {
                _state.value = PoiState(message = "Location unavailable. Allow location access and turn on GPS.")
                return
            }
            loadAt(position, announce)
        }
    }

    /**
     * Called on every position fix while the app is visible or a car is connected. Distances and
     * the ranking update on each fix; places are only re-queried after moving [REFETCH_METERS].
     */
    suspend fun onPosition(position: LatLon, announce: Boolean) {
        mutex.withLock { loadAt(position, announce) }
    }

    private suspend fun loadAt(position: LatLon, announce: Boolean) {
        val current = settings.settings.value
        val key = FetchKey(current.categories, current.radiusMeters)
        val origin = fetchedFrom
        if (key != fetchedFor || origin == null || distanceMeters(origin, position) > REFETCH_METERS) {
            try {
                candidates = finder.find(position, current.radiusMeters, current.categories)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = PoiState(message = "Couldn't load places right now (${e.message ?: "unknown error"}).")
                return
            }
            fetchedFrom = position
            fetchedFor = key
        }
        here = position
        publish(current.radiusMeters, announce)
    }

    /** Hides the current suggestion and shows the next one, announcing it if [announce] is set. */
    suspend fun next(announce: Boolean) {
        mutex.withLock {
            val shown = _state.value.current ?: return
            settings.dismiss(shown.poi.id)
            publish(settings.settings.value.radiusMeters, announce)
        }
    }

    fun speakCurrent() {
        _state.value.current?.let { narrator.speak(speechFor(it)) }
    }

    fun stopSpeaking() = narrator.stop()

    /** Forgets the last announcement so the next suggestion is read out again, e.g. on a new car connection. */
    fun resetAnnouncement() {
        announcedId = null
    }

    private fun publish(radiusMeters: Int, announce: Boolean) {
        val origin = here ?: return
        val ranked = PoiRanker.rank(candidates, origin, radiusMeters, settings.dismissedIds())
        val top = ranked.firstOrNull()
        _state.value = when {
            top != null -> PoiState(
                current = top,
                queued = ranked.size - 1,
                upcoming = ranked.drop(1),
                position = origin,
            )
            candidates.isEmpty() -> PoiState(message = "No places found within $radiusMeters m.", position = origin)
            else -> PoiState(message = "No more suggestions nearby.", position = origin)
        }
        if (top != null && announce && top.poi.id != announcedId) {
            announcedId = top.poi.id
            narrator.speak(speechFor(top))
        }
    }

    private data class FetchKey(val categories: Set<PoiCategory>, val radiusMeters: Int)

    private companion object {
        const val REFETCH_METERS = 400.0
    }
}
