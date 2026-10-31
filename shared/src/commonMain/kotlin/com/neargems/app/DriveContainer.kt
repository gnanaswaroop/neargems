package com.neargems.app

import com.neargems.data.AppSettings
import com.neargems.data.Http
import com.neargems.data.OverpassSource
import com.neargems.data.PoiFinder
import com.neargems.data.PoiRepository
import com.neargems.data.PoiState
import com.neargems.data.SettingsStore
import com.neargems.data.Visit
import com.neargems.data.VisitedHistory
import com.neargems.data.VisitedPlace
import com.neargems.data.VisitStore
import com.neargems.data.WikipediaSource
import com.neargems.domain.LatLon
import com.neargems.domain.bearingDegrees
import com.neargems.domain.distanceMeters
import com.neargems.domain.Poi
import com.neargems.domain.Holding
import com.neargems.domain.bearingDegrees
import com.neargems.domain.compassPoint
import com.neargems.domain.formatDistance
import com.neargems.domain.PhonePosture
import com.neargems.domain.SunPosition
import com.neargems.domain.classifyPosture
import com.neargems.domain.sunPosition
import com.neargems.domain.RankedPoi
import com.neargems.platform.KeyValueStore
import com.neargems.platform.HeadingSource
import com.neargems.platform.LocationSource
import com.neargems.platform.MapOpener
import com.neargems.platform.MemorySeenStore
import com.neargems.platform.Narrator
import com.neargems.platform.SeenStore
import com.neargems.platform.NoPlaceNotifier
import com.neargems.platform.PlaceNotifier
import com.neargems.platform.PostureSource
import com.neargems.platform.NoPostureSource
import com.neargems.platform.SpeechVoice
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Everything the platform must supply. Each platform builds one of these in its entry point. */
class PlatformServices(
    val http: HttpClient,
    val keyValues: KeyValueStore,
    val location: LocationSource,
    val headings: HeadingSource,
    val narrator: Narrator,
    val maps: MapOpener,
    /** True while the phone is connected to a car display (Android Auto or CarPlay). */
    val carConnected: Flow<Boolean>,
    /** Device language code, used to pick the Wikipedia edition. */
    val language: String,
    val clock: () -> Long,
    /** Which places the user has seen, for the New and Seen badges. A database on Android. */
    val seen: SeenStore = MemorySeenStore(),
    /** Gravity readings, for telling portrait from landscape and mounted from held. */
    val posture: PostureSource = NoPostureSource,
    val notifier: PlaceNotifier = NoPlaceNotifier,
)

/**
 * Shared application state. Decides when suggestions appear on their own: while a car is
 * connected it announces the nearest interesting place and refreshes as the car moves.
 */
class DriveContainer(platform: PlatformServices) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val narrator = platform.narrator
    private val maps = platform.maps
    /** The GPS, unless test mode has chosen a position to use instead. */
    private val location: LocationSource = object : LocationSource {
        override suspend fun current(): LatLon? = settings.settings.value.testLocation ?: platform.location.current()

        override fun updates(): Flow<LatLon> = platform.location.updates().map { fix ->
            settings.settings.value.testLocation ?: fix
        }
    }

    private val clock = platform.clock

    val settings = SettingsStore(platform.keyValues, platform.clock)

    private val visitedHistory = VisitedHistory(platform.keyValues)

    /** Places the user has been close to, stored on this device only. */
    private val visitStore = VisitStore(platform.keyValues, platform.clock)
    val visits: StateFlow<List<Visit>> = visitStore.visits

    private val seenStore = platform.seen
    private val _seenIds = MutableStateFlow(seenStore.seenIds())

    /** Ids of the places the user has seen. Anything else is New. */
    val seenIds: StateFlow<Set<String>> = _seenIds.asStateFlow()

    /** Marks a place as seen: shown and moved on from, opened, passed with Next, or read aloud. */
    fun markSeen(poi: Poi) {
        if (poi.id in _seenIds.value) return
        seenStore.markSeen(poi.id, poi.name, poi.category.label, clock())
        _seenIds.value = _seenIds.value + poi.id
    }

    val repository = PoiRepository(
        settings = settings,
        finder = PoiFinder(
            overpass = OverpassSource(Http(platform.http)),
            wikipedia = WikipediaSource(Http(platform.http), platform.language),
        ),
        location = location,
        narrator = MutingNarrator(platform.narrator) { settings.settings.value.muteAudio },
        // Visits are not recorded in this release. Seeing a suggestion is not visiting a place.
        visits = null,
    )

    val state: StateFlow<PoiState> = repository.state

    /** The voices the device can read with, for the Voice setting. */
    val voices: StateFlow<List<SpeechVoice>> = narrator.voices

    fun now(): Long = clock()

    val carConnected: StateFlow<Boolean> = combine(platform.carConnected, settings.settings) { projecting, s ->
        projecting || s.simulateCar
    }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, false)

    private val _sun = MutableStateFlow<SunPosition?>(null)

    /** Where the sun is, seen from the last known position. Updated every [SUN_TICK_MS]. Null until a fix arrives. */
    val sun: StateFlow<SunPosition?> = _sun.asStateFlow()

    /** Set by the platform while its UI is on screen. */
    private val visible = MutableStateFlow(false)

    private val heading = MutableStateFlow<Float?>(null)

    /** Which way the car is travelling, from successive GPS fixes, or null until it has moved. */
    private val travel = MutableStateFlow<Float?>(null)

    /** Speed from the last two GPS fixes, in metres per second. */
    private val speed = MutableStateFlow(0.0)
    private val gravityReading = MutableStateFlow(0f to 9.8f)
    private var steadySince = clock()
    private var lastGravity: Pair<Float, Float>? = null
    private val _posture = MutableStateFlow(PhonePosture.UNKNOWN)
    private val notifier = platform.notifier
    private val announcedIds = mutableSetOf<String>()

    /** How the phone is held and whether it is mounted, updated every second. Drives the heading rule. */
    val posture: StateFlow<PhonePosture> = _posture.asStateFlow()

    /**
     * The direction the pointer is measured from. When the phone is mounted, it is the direction of
     * travel, because the phone's own direction is unreliable there; when it is held, the phone's heading.
     */
    val headingDegrees: StateFlow<Float?> = combine(heading, travel, posture) { phone, moving, now ->
        if (now.holding == Holding.MOUNTED && moving != null) moving else phone
    }.stateIn(scope, SharingStarted.Eagerly, null)

    init {
        scope.launch {
            var anchor: LatLon? = null
            var previous: Pair<LatLon, Long>? = null
            state.map { it.position }.filterNotNull().distinctUntilChanged().collect { fix ->
                val now = clock()
                previous?.let { (last, at) ->
                    val seconds = (now - at) / 1000.0
                    if (seconds > 0) speed.value = distanceMeters(last, fix) / seconds
                }
                previous = fix to now
                val from = anchor
                if (from == null) {
                    anchor = fix
                } else if (distanceMeters(from, fix) >= MOVING_METERS) {
                    travel.value = bearingDegrees(from, fix).toFloat()
                    anchor = fix
                }
            }
        }
        // The place on screen stays in the notification shade. A new place is announced with a soft
        // sound, but only while audio is on, and not for the first place found when the app starts.
        // The live notification always says what the app is doing: the place on screen, or the search status.
        scope.launch {
            state.map { liveStatus(it) }.distinctUntilChanged().collect { (title, detail) ->
                notifier.liveUpdate(title, detail)
            }
        }
        // A new place makes a soft sound, but only while audio is on, and not for the first place found.
        scope.launch {
            state.map { it.current }.distinctUntilChanged { a, b -> a?.poi?.id == b?.poi?.id }.collect { ranked ->
                if (ranked == null) return@collect
                val firstFound = announcedIds.isEmpty()
                if (announcedIds.add(ranked.poi.id) && !firstFound && !settings.settings.value.muteAudio) {
                    notifier.newPlace("New place: ${ranked.poi.name}", placeDetail(ranked, state.value.position))
                }
            }
        }
        // Gravity tells the orientation. A change of more than a little means the phone is being moved.
        scope.launch {
            platform.posture.gravity().collect { (x, y) ->
                val last = lastGravity
                if (last == null || abs(last.first - x) + abs(last.second - y) > STEADY_CHANGE) {
                    steadySince = clock()
                    lastGravity = x to y
                }
                gravityReading.value = x to y
            }
        }
        scope.launch {
            while (true) {
                val (x, y) = gravityReading.value
                _posture.value = classifyPosture(
                    gravityX = x,
                    gravityY = y,
                    carConnected = carConnected.value,
                    steadySeconds = (clock() - steadySince) / 1000,
                    speedMps = speed.value,
                )
                delay(POSTURE_TICK_MS)
            }
        }
    }

    init {
        narrator.useVoice(settings.settings.value.voice)
        // The compass only moves while the screen is visible.
        scope.launch {
            visible.collectLatest { onScreen ->
                if (onScreen) {
                    platform.headings.headings().collect { heading.value = it }
                } else {
                    heading.value = null
                }
            }
        }
        // Live position only while someone can see it or a car is listening, to save battery.
        scope.launch {
            combine(visible, carConnected) { onScreen, connected -> onScreen || connected }
                .distinctUntilChanged()
                .collectLatest { live ->
                    if (live) {
                        location.updates().collect { position ->
                            repository.onPosition(position, announce = shouldAnnounce())
                        }
                    }
                }
        }
        // The sun is worked out from the latest fix, and again every tick while the fix stays put.
        scope.launch {
            state.map { it.position }.filterNotNull().distinctUntilChanged().collectLatest { position ->
                while (true) {
                    _sun.value = sunPosition(position, clock())
                    delay(SUN_TICK_MS)
                }
            }
        }
        scope.launch {
            carConnected.collectLatest { connected ->
                if (!connected) {
                    repository.stopSpeaking()
                    return@collectLatest
                }
                repository.resetAnnouncement()
                while (true) {
                    refreshAsync()
                    delay(REFRESH_INTERVAL_MS)
                }
            }
        }
    }

    fun refresh() {
        scope.launch { refreshAsync() }
    }

    /** Searches again from scratch, for a pull-down, so the searching state stays up while the new results load. */
    fun pullToRefresh() {
        scope.launch { repository.refresh(announce = shouldAnnounce(), force = true) }
    }

    fun next() {
        val place = shownPlace.value ?: state.value.current ?: return
        // Passing a place on means it has been seen.
        markSeen(place.poi)
        shownPlace.value = null
        scope.launch { repository.next(announce = shouldAnnounce(), shown = place) }
    }

    /** Hides the place for good, because the user has already been there. */
    fun markVisited(ranked: RankedPoi) {
        settings.markVisited(ranked.poi.id)
        visitedHistory.add(ranked.poi, clock())
        shownPlace.value = null
        scope.launch { refreshAsync() }
    }

    /** Places marked as visited, newest first, for the History screen. */
    val visited: StateFlow<List<VisitedPlace>> = visitedHistory.places

    /** Brings a visited place back into the suggestions. */
    fun showAgain(id: String) {
        settings.unmarkVisited(id)
        visitedHistory.remove(id)
        scope.launch { refreshAsync() }
    }

    fun openVisited(place: VisitedPlace) = maps.open(
        Poi(place.id, place.name, place.category, place.location, snippet = "", interest = 0.0),
    )

    fun previous() {
        scope.launch { repository.previous(announce = shouldAnnounce()) }
    }

    /** The card on screen. Read aloud, Next and Mark as visited act on it; null means the top pick. */
    private val shownPlace = MutableStateFlow<RankedPoi?>(null)

    fun showing(ranked: RankedPoi?) {
        shownPlace.value = ranked
    }

    fun speakCurrent() {
        val place = shownPlace.value ?: state.value.current ?: return
        markSeen(place.poi)
        repository.speakPlace(place)
    }

    fun openMap(poi: Poi) = maps.open(poi)

    fun openUrl(url: String) = maps.openUrl(url)

    /** The platform calls this when its UI starts and stops being visible. */
    fun setVisible(onScreen: Boolean) {
        visible.value = onScreen
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        scope.launch {
            val before = settings.settings.value
            settings.update(transform)
            val after = settings.settings.value
            // Muting also stops anything already being read aloud.
            if (after.muteAudio) narrator.stop()
            narrator.useVoice(after.voice)
            // A change to what is searched for always starts a fresh search, not a re-ranking of the old results.
            val criteriaChanged = before.categories != after.categories ||
                before.keywords != after.keywords ||
                before.radiusMeters != after.radiusMeters
            if (criteriaChanged) repository.refresh(announce = shouldAnnounce(), force = true) else refreshAsync()
        }
    }

    /** Picks a voice and reads a short sample, so the choice can be heard straight away. */
    fun chooseVoice(id: String?) {
        updateSettings { it.copy(voice = id) }
        narrator.useVoice(id)
        // Read the place on screen so the voice is heard on something real; a sample place when there is none.
        val top = state.value.current
        if (top != null) repository.speakPlace(top) else narrator.speak(VOICE_SAMPLE)
    }

    fun restoreDismissed() {
        scope.launch {
            settings.clearDismissed()
            settings.clearVisited()
            refreshAsync()
        }
    }

    private suspend fun refreshAsync() {
        repository.refresh(announce = shouldAnnounce())
    }

    /** The title and line for the live notification: the place on screen, or the search status when there is none. */
    private fun liveStatus(s: PoiState): Pair<String, String> {
        val ranked = s.current ?: return "NearGems" to when {
            s.loading || s.searching -> "Finding places near you"
            else -> s.message ?: "No places nearby"
        }
        return ranked.poi.name to placeDetail(ranked, s.position)
    }

    private fun placeDetail(ranked: RankedPoi, from: LatLon?): String {
        val bearing = from?.let { bearingDegrees(it, ranked.poi.location) }
        return formatDistance(ranked.distanceMeters) + bearing?.let { " · ${compassPoint(it)}" }.orEmpty()
    }

    private fun shouldAnnounce(): Boolean =
        carConnected.value && settings.settings.value.announceOnCar

    companion object {
        const val REFRESH_INTERVAL_MS = 60_000L
        /** The sun moves about a quarter of a degree in a minute, so half a minute is plenty. */
        const val SUN_TICK_MS = 30_000L
        const val VOICE_SAMPLE = "Harbour Lane Café. A café about 150 metres away, with tables outside. Open until ten tonight."
        /** Fixes closer together than this are GPS jitter, not movement, so they do not change the travel direction. */
        const val MOVING_METERS = 15.0
        /** Gravity readings this far apart, summed over both axes, count as the phone being moved. */
        const val STEADY_CHANGE = 1.5f
        const val POSTURE_TICK_MS = 1_000L
    }
}
