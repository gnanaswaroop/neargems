package com.neargem.app

import com.neargem.data.AppSettings
import com.neargem.data.Http
import com.neargem.data.OverpassSource
import com.neargem.data.PoiFinder
import com.neargem.data.PoiRepository
import com.neargem.data.PoiState
import com.neargem.data.SettingsStore
import com.neargem.data.Visit
import com.neargem.data.VisitStore
import com.neargem.data.WikipediaSource
import com.neargem.domain.LatLon
import com.neargem.domain.Poi
import com.neargem.platform.KeyValueStore
import com.neargem.platform.HeadingSource
import com.neargem.platform.LocationSource
import com.neargem.platform.MapOpener
import com.neargem.platform.Narrator
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    /** Places the user has been close to, stored on this device only. */
    private val visitStore = VisitStore(platform.keyValues, platform.clock)
    val visits: StateFlow<List<Visit>> = visitStore.visits

    val repository = PoiRepository(
        settings = settings,
        finder = PoiFinder(
            overpass = OverpassSource(Http(platform.http)),
            wikipedia = WikipediaSource(Http(platform.http), platform.language),
        ),
        location = location,
        narrator = platform.narrator,
        visits = visitStore,
    )

    val state: StateFlow<PoiState> = repository.state

    fun now(): Long = clock()

    val carConnected: StateFlow<Boolean> = combine(platform.carConnected, settings.settings) { projecting, s ->
        projecting || s.simulateCar
    }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, false)

    /** Set by the platform while its UI is on screen. */
    private val visible = MutableStateFlow(false)

    private val heading = MutableStateFlow<Float?>(null)

    /** Which way the phone faces, in degrees from north, or null when unknown. For the compass pointer. */
    val headingDegrees: StateFlow<Float?> = heading.asStateFlow()

    init {
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

    fun next() {
        scope.launch { repository.next(announce = shouldAnnounce()) }
    }

    fun previous() {
        scope.launch { repository.previous(announce = shouldAnnounce()) }
    }

    fun speakCurrent() = repository.speakCurrent()

    fun openMap(poi: Poi) = maps.open(poi)

    fun openUrl(url: String) = maps.openUrl(url)

    /** The platform calls this when its UI starts and stops being visible. */
    fun setVisible(onScreen: Boolean) {
        visible.value = onScreen
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        scope.launch {
            settings.update(transform)
            refreshAsync()
        }
    }

    fun restoreDismissed() {
        scope.launch {
            settings.clearDismissed()
            refreshAsync()
        }
    }

    private suspend fun refreshAsync() {
        repository.refresh(announce = shouldAnnounce())
    }

    private fun shouldAnnounce(): Boolean =
        carConnected.value && settings.settings.value.announceOnCar

    private companion object {
        const val REFRESH_INTERVAL_MS = 60_000L
    }
}
