package com.drivescout.app

import com.drivescout.data.AppSettings
import com.drivescout.data.Http
import com.drivescout.data.OverpassSource
import com.drivescout.data.PoiFinder
import com.drivescout.data.PoiRepository
import com.drivescout.data.PoiState
import com.drivescout.data.SettingsStore
import com.drivescout.data.WikipediaSource
import com.drivescout.domain.Poi
import com.drivescout.platform.KeyValueStore
import com.drivescout.platform.LocationSource
import com.drivescout.platform.MapOpener
import com.drivescout.platform.Narrator
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything the platform must supply. Each platform builds one of these in its entry point. */
class PlatformServices(
    val http: HttpClient,
    val keyValues: KeyValueStore,
    val location: LocationSource,
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

    val settings = SettingsStore(platform.keyValues, platform.clock)
    val repository = PoiRepository(
        settings = settings,
        finder = PoiFinder(
            overpass = OverpassSource(Http(platform.http)),
            wikipedia = WikipediaSource(Http(platform.http), platform.language),
        ),
        location = platform.location,
        narrator = platform.narrator,
    )

    val state: StateFlow<PoiState> = repository.state

    val carConnected: StateFlow<Boolean> = combine(platform.carConnected, settings.settings) { projecting, s ->
        projecting || s.simulateCar
    }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, false)

    init {
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

    fun speakCurrent() = repository.speakCurrent()

    fun openMap(poi: Poi) = maps.open(poi)

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
