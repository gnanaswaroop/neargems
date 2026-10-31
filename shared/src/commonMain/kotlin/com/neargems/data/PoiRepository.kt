package com.neargems.data

import com.neargems.domain.LatLon
import com.neargems.domain.PoiCategory
import com.neargems.domain.PoiRanker
import com.neargems.domain.parseKeywords
import com.neargems.domain.RankedPoi
import com.neargems.domain.distanceMeters
import com.neargems.domain.speechFor
import com.neargems.platform.LocationSource
import com.neargems.platform.Narrator
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
    /** A warning about the search that does not stop the places from showing, e.g. a keyword search that failed. */
    val warning: String? = null,
    /** Where the device was at the last fix, so the UI can show live direction and time. */
    val position: LatLon? = null,
    /** True when a place passed with Next can be brought back with [PoiRepository.previous]. */
    val hasPrevious: Boolean = false,
    /** The place [PoiRepository.previous] would bring back, measured from the last fix, or null. */
    val previous: RankedPoi? = null,
    /** True while a search is still running, so the UI can show progress as results trickle in. */
    val searching: Boolean = false,
)

class PoiRepository(
    private val settings: SettingsStore,
    private val finder: PoiFinder,
    private val location: LocationSource,
    private val narrator: Narrator,
    /** Where visits are recorded. Optional so that tests can run without a history. */
    private val visits: VisitStore? = null,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(PoiState(loading = true, message = "Finding places nearby…"))
    val state: StateFlow<PoiState> = _state.asStateFlow()

    private var here: LatLon? = null
    private var fetchedFrom: LatLon? = null
    private var fetchedFor: FetchKey? = null
    private var candidates: List<com.neargems.domain.Poi> = emptyList()
    private var announcedId: String? = null
    /** Places passed with Next this session, most recent last, so they can be brought back. */
    private val passed = ArrayDeque<RankedPoi>()

    /**
     * Re-reads the device location. Places are only re-queried after moving [REFETCH_METERS]
     * or changing categories or radius; otherwise the cached candidates are re-ranked.
     */
    suspend fun refresh(announce: Boolean, force: Boolean = false) {
        mutex.withLock {
            _state.update { it.copy(loading = true) }
            val position = location.current()
            if (position == null) {
                _state.value = PoiState(message = "Location unavailable. Allow location access and turn on GPS.")
                return
            }
            // A pull-down asks for a fresh search, rather than re-ranking the places already found.
            if (force) fetchedFor = null
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
        val key = FetchKey(current.categories, current.radiusMeters, parseKeywords(current.keywords))
        val origin = fetchedFrom
        if (key != fetchedFor || origin == null || distanceMeters(origin, position) > REFETCH_METERS) {
            here = position
            // Keep the places already found while the new search runs, unless the categories changed:
            // those belong to a different search. Show them at once, then let the new results trickle in.
            val kept = if (fetchedFor?.categories == key.categories) candidates else emptyList()
            candidates = kept
            keywordWarning = null
            publish(current.radiusMeters, announce = false, searching = true)
            try {
                candidates = finder.find(
                    position,
                    current.radiusMeters,
                    current.categories,
                    key.keywords,
                    onWarning = { keywordWarning = it },
                ) { found ->
                    candidates = (found + kept).distinctBy { it.id }
                    publish(current.radiusMeters, announce = false, searching = true)
                }
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
    suspend fun next(announce: Boolean, shown: RankedPoi? = null) {
        mutex.withLock {
            val current = shown ?: _state.value.current ?: return
            settings.dismiss(current.poi.id)
            passed.addLast(current)
            publish(settings.settings.value.radiusMeters, announce)
        }
    }

    /** Brings back the place passed with the last Next, and shows it again. */
    suspend fun previous(announce: Boolean) {
        mutex.withLock {
            val back = passed.removeLastOrNull() ?: return
            settings.undismiss(back.poi.id)
            publish(settings.settings.value.radiusMeters, announce)
        }
    }

    fun speakCurrent() {
        _state.value.current?.let { narrator.speak(speechFor(it)) }
    }

    /** Reads aloud the place the user is looking at, which may not be the top pick. */
    fun speakPlace(ranked: RankedPoi) {
        narrator.speak(speechFor(ranked))
    }

    fun stopSpeaking() = narrator.stop()

    /** Forgets the last announcement so the next suggestion is read out again, e.g. on a new car connection. */
    fun resetAnnouncement() {
        announcedId = null
    }

    /** The warning about the latest search, or null when every source answered. */
    private var keywordWarning: String? = null

    private fun publish(radiusMeters: Int, announce: Boolean, searching: Boolean = false) {
        val origin = here ?: return
        val ranked = PoiRanker.rank(
            candidates,
            origin,
            radiusMeters,
            settings.dismissedIds() + settings.visitedIds(),
        )
        recordArrivals(ranked)
        val top = ranked.firstOrNull()
        // The place Back would bring back. It is always shown, even after moving past the search radius,
        // so the left-hand card is there whenever Back is; only its distance is taken from here.
        val previous = passed.lastOrNull()?.let { RankedPoi(it.poi, distanceMeters(origin, it.poi.location), it.poi.interest) }
        _state.value = when {
            top != null -> PoiState(
                current = top,
                queued = ranked.size - 1,
                upcoming = ranked.drop(1),
                position = origin,
                hasPrevious = passed.isNotEmpty(),
                previous = previous,
                searching = searching,
            )
            // Nothing yet, but the search is still running: keep showing that it is searching.
            searching -> PoiState(
                loading = true,
                position = origin,
                hasPrevious = passed.isNotEmpty(),
                previous = previous,
                searching = true,
            )
            candidates.isEmpty() -> PoiState(
                message = "No places found within $radiusMeters m.",
                position = origin,
                hasPrevious = passed.isNotEmpty(),
                previous = previous,
            )
            else -> PoiState(
                message = "No more suggestions nearby.",
                position = origin,
                hasPrevious = passed.isNotEmpty(),
                previous = previous,
            )
        }
        _state.value = _state.value.copy(warning = keywordWarning)
        if (top != null && announce && top.poi.id != announcedId) {
            announcedId = top.poi.id
            narrator.speak(speechFor(top))
        }
    }

    /** A place within [ARRIVAL_METERS] of the user counts as visited. */
    private fun recordArrivals(ranked: List<RankedPoi>) {
        val history = visits ?: return
        ranked.filter { it.distanceMeters <= ARRIVAL_METERS }.forEach {
            history.record(it.poi.id, it.poi.name, it.poi.category.label)
        }
    }

    private data class FetchKey(val categories: Set<PoiCategory>, val radiusMeters: Int, val keywords: List<String>)

    private companion object {
        const val REFETCH_METERS = 400.0
        const val ARRIVAL_METERS = 50.0
    }
}
