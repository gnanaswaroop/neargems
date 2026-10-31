# NearGems: low-level design

This document describes the code: modules, classes, data formats and the main sequences. For the overall
picture, see [high-level-design.md](high-level-design.md).

## Repository layout

```
shared/                         Kotlin Multiplatform module
  src/commonMain/kotlin/com/neargems/
    domain/                     pure logic, no platform code
    data/                       fetching, storage, repository
    app/                        DriveContainer and the narrator wrapper
    platform/                   interfaces the platform must implement
    ui/                         Compose screens and shared components
  src/commonMain/composeResources/drawable/   logo and mark artwork
androidApp/                     Android application
  src/main/kotlin/com/neargems/android/
    MainActivity.kt, NearGemsApplication.kt   entry points, wiring
    platform/                   implementations of the platform interfaces
    service/CarWatchService.kt  foreground car watch
    car/CarConnectionMonitor.kt car connection detection
  src/test/                     JVM unit tests (run without a device)
  src/sharedTest/               test fixtures shared by unit and device tests
  src/androidTest/              device tests (run on an emulator or phone)
docs/                           design notes, screenshots, store listing
```

## Domain (`domain/`)

| File | Contents |
| --- | --- |
| `Poi.kt` | `PoiCategory` (label, OSM amenities), `Poi` (id, name, category, location, snippet, interest, photo, link), `Photo`, `RankedPoi` (poi, distance, score). |
| `Geo.kt` | `LatLon`, `distanceMeters` (haversine), `bearingDegrees`, `compassPoint`. |
| `PoiRanker.kt` | `rank(candidates, from, radiusMeters, excludedIds)`: drops excluded and transport stops, computes distance, keeps places within the radius, and scores each place as `interest - MAX_DISTANCE_PENALTY * distance / radius`. Sorted best first. |
| `Keywords.kt` | `parseKeywords(text)` (comma split, trim, lower-case, de-duplicate); `Poi.matchesKeywords`; `Poi.isTransportStop()` for stations and stops. |
| `KeywordSuggestions.kt` | `SUGGESTED_KEYWORDS` (62 ideas) and `suggestKeywords(typed, chosen, limit)`. |
| `Posture.kt` | `Orientation` (portrait, landscape), `Holding` (hand-held, mounted), `PhonePosture`, `classifyPosture(gravityX, gravityY, carConnected, steadySeconds, speedMps)`. Mounted when a car display is connected, or when steady for 20 s at 4 m/s or more. |
| `Sun.kt` | `sunPosition(observer, epochMillis)`: azimuth from north and elevation, accurate to a fraction of a degree. |
| `Summary.kt` | `summarise(text, maxChars)`: whole sentences that fit a character budget. |
| `Format.kt` | Distance and time formatting, walk and drive minutes. |

## Data (`data/`)

### Place sources

**`OverpassSource`** queries the OpenStreetMap Overpass API.

- `fetch(center, radius, categories)` asks for named nodes, ways and relations whose `amenity` is one of the
  chosen categories' amenities, within the radius. Results are parsed by `parse`.
- `fetchNamed(center, radius, keywords)` asks for names that contain any keyword (case-insensitive, escaped). The
  keywords are split by `namedBatches`, eight at a time. A result without a known amenity is labelled Landmark.
- `ENDPOINTS` lists the main server and two public mirrors. `withRetries` tries them in turn, up to `ATTEMPTS` (3)
  times, pausing 1 s and then 2 s between attempts. A failure after the last attempt is thrown to the caller.

**`WikipediaSource`** uses the MediaWiki geosearch to find nearby articles as landmarks, with summaries, photos
and links. The language follows the device.

**`PoiFinder.find(center, radiusMeters, categories, keywords, onWarning, onPartial)`** runs the three sources in
parallel (category search, keyword search, Wikipedia). Each answer is merged, de-duplicated by id, and passed to
`onPartial` so results appear early. If the keyword search fails, `onWarning(KEYWORD_WARNING)` is called. If every
source fails, the exception is thrown.

### Repository

**`PoiRepository`** owns the search and the list the screens show.

- `state: StateFlow<PoiState>`, with `loading`, `searching`, `current`, `queued`, `upcoming`, `previous`,
  `hasPrevious`, `message`, `warning` and `position`.
- `refresh(announce, force)` reads the position, then `loadAt`. `loadAt` searches again when the categories, radius
  or keywords change, when the position moves beyond the refetch distance, or when `force` is set (a pull-down).
  Otherwise it re-ranks the candidates already held.
- `publish` ranks the candidates, excluding dismissed and visited ids, and builds the state. It copies the latest
  keyword warning into every state.
- `next(announce, shown)` dismisses the shown place for 12 hours and moves on. `previous()` brings back the last
  place passed. `speakPlace` and `speakCurrent` read through the narrator.
- A `Mutex` serialises searches so updates never interleave.

### Settings and storage

**`SettingsStore`** wraps a `KeyValueStore` (SharedPreferences on Android) and exposes `settings: StateFlow<AppSettings>`.

| Key | Meaning |
| --- | --- |
| `categories` | comma-separated `PoiCategory` names |
| `keywords` | comma-separated keywords, as typed |
| `radius_m` | search radius in metres |
| `place_view` | `CARDS` or `LIST` |
| `theme_mode` | `SYSTEM`, `LIGHT` or `DARK` |
| `announce_car` | read the top place aloud when a car connects |
| `mute_audio` | silence all speech |
| `watch_in_background` | keep the car watch service running |
| `voice` | the chosen voice id, or absent for the device default |
| `test_lat`, `test_lon` | a chosen position, instead of GPS, when both are present |
| `dismissed` | lines of `id|epochMillis`, expired after 12 hours |
| `marked_visited` | newline-separated visited ids, used to hide places |

`simulate_car` is not stored, so a pretend car never persists across launches.

**`VisitedHistory`** keeps the places marked as visited, with their details, under `visited_places`. Each line is
`id|name|category|lat|lon|epochMillis`, newest first. Separators inside names are replaced.
`VisitedPlace.matches(origin, radius, categories, keywords)` decides which visited places are counted on the home
screen: within the radius, and in a chosen category or with a keyword in the name.

**`VisitStore`** records proximity visits. It is not written to in the current release.

## Application state (`app/`)

**`DriveContainer`** is the shared model the screens read. It is built from `PlatformServices`:

```
PlatformServices(http, keyValues, location, headings, narrator, maps, carConnected,
                 language, clock, seen, posture, notifier)
```

Its main flows:

- `headingDegrees = combine(heading, travel, posture)`: the travel bearing when mounted and moving, otherwise the
  phone's heading.
- `travel` is set from GPS fixes at least 15 m apart. `speed` is the distance over time between the last two fixes.
- `posture` is classified every second from gravity, car connection, steadiness and speed.
- `sun` is recomputed every 30 seconds from the latest position.
- `state.current` changes raise a live notification and, for a new place with audio on, a new-place alert. The
  first place found at launch is not announced.
- A car connection starts a 60-second refresh loop and, with announcements on, speaks the top place.
- `markVisited`, `showAgain`, `chooseVoice`, `pullToRefresh` and `next` are the user actions.

**`MutingNarrator`** wraps the platform narrator and drops speech while audio is muted. Its `voices` and `useVoice`
are passed through.

## Platform interfaces (`platform/Platform.kt`)

| Interface | Purpose | Android implementation |
| --- | --- | --- |
| `LocationSource` | `current()` and `updates()` | `AndroidLocationSource` (Fused Location) |
| `HeadingSource` | compass heading in degrees | `AndroidHeadingSource` (rotation vector) |
| `PostureSource` | gravity `(x, y)` | `AndroidPostureSource` (gravity sensor, accelerometer fallback) |
| `Narrator` | `speak`, `stop`, `voices`, `useVoice` | `AndroidNarrator` (TextToSpeech, navigation audio focus) |
| `MapOpener` | open a place or a URL | `AndroidMapOpener` (geo intent) |
| `KeyValueStore` | string storage | `SharedPrefsStore` |
| `SeenStore` | the seen-places list | `AndroidSeenStore` (SQLite, `seen_places`) |
| `PlaceNotifier` | live and new-place notifications | `AndroidPlaceNotifier` |

`carConnected` is a `Flow<Boolean>` from `CarConnectionMonitor`, which reports Android Auto or a car display.

### Android specifics

- `AndroidHeading`: `headingFromRotation` uses whichever of the top edge and the back camera is more horizontal, so a
  rolled mount does not skew the reading.
- `AndroidNarrator`: lists the installed voices in the app's language, best of each accent first, and applies the
  chosen one. Speech uses `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE`, so music ducks.
- `AndroidPlaceNotifier`: channels `place-on-screen` (quiet, ongoing) and `new-places` (default sound). The live
  notification uses `Notification.Builder.setRequestPromotedOngoing` on Android 16 and later. Both open
  `MainActivity` when tapped.
- `CarWatchService`: a foreground service of type location with `START_STICKY`. It stops in `onTaskRemoved`, so
  swiping the app away lets the process end. `MainActivity` restarts it on launch when the switch is on.
- `MainActivity.quit()`: stops the watch, cancels the live notification, removes the task and ends the process.

## User interface (`ui/`)

`NearGemsApp` is the root composable. `NearGemsContent` holds the screen state (`showSettings`, `showHistory`,
location disclosure) and provides `LocalSeenIds`, `LocalSunPosition` and `LocalPhoneHeading` to the cards.

Layout:

- Phones: one column. The top bar, then the warning banner, the keyword tags, the nearby-visited line, and the
  card pager, with the action bar at the bottom.
- Two-pane (width at least 600 dp): the queue on the left (240 dp below 720 dp, 340 dp above), the card on the
  right.
- Landscape phones: the card takes the full width, with its photo beside the details.

Components:

| Component | Role |
| --- | --- |
| `TopBar` | car status, mute, History, Settings, Quit |
| `SuggestionArea` / `SuggestionPager` | the card pager, with the departing-card animation |
| `SuggestionCard` / `WideSuggestionCard` | a place: photo, text, `DistanceDetails`, visited button, `MiniMap` |
| `DistanceDetails` | distance, direction, walk and drive minutes, the compass pointer and the sun readout |
| `CompassPointer` | the pointer, turned by `headingDegrees`, with a sun dot on the ring |
| `MiniMap` | OpenStreetMap tiles, turned so the phone's direction is up, with an N marker |
| `SearchingState` / `EmptyState` | searching and nothing-found screens, with the pull-down hint |
| `ActionBar` | Back, Read aloud, Next |
| `SettingsPanel` | categories (`BulletChip`), keywords (`KeywordTags`), radius, appearance, voice (`VoiceDropdown`), places, car switches, test location |
| `HistoryPanel` | visited places, with Show again and Open map |
| `WarningBanner` | the search warning across the top |

Interactions:

- The search warning and the keyword tags sit above the cards.
- Keyword ideas appear only when something is typed, as a preview of up to eight matching `BulletChip`s.
- A pull-down uses `PullToRefreshBox`, which works where the card can scroll. `pullToRefresh()` forces a fresh search.
- The departing card shrinks to 80 % over 1.8 s, then the next card grows in over 0.5 s.

## Key sequences

### Search

```
UI pull / settings change / movement
  -> DriveContainer.refresh / pullToRefresh
    -> PoiRepository.refresh(force?)  [mutex]
      -> LocationSource.current()
      -> PoiFinder.find(...)  [three sources in parallel]
           -> OverpassSource.fetch / fetchNamed  [batches, mirrors, retries]
           -> WikipediaSource.fetch
      -> publish(): rank, exclude dismissed and visited, build PoiState
  -> StateFlow emits -> UI recomposes; notifier and narrator react
```

### Car connection

```
CarConnectionMonitor emits projecting = true
  -> DriveContainer: repository.resetAnnouncement(), refresh every 60 s
  -> top place spoken if announceOnCar and not muted
  -> CarWatchService keeps the process alive when the watch switch is on
```

### Keyword search

```
SettingsPanel types "church," -> updateSettings(keywords = "church")
  -> PoiRepository.loadAt: FetchKey changed -> searches again
  -> PoiFinder: fetchNamed(["church"]) -> one batch -> Overpass names regex
       fails after retries -> onWarning(KEYWORD_WARNING) -> state.warning
  -> ranked candidates shown; keyword places sit beside category places
```

## Storage and privacy

- No server is run by the developer. Requests go to the public services listed in the design, with the position
  as the only user data sent.
- Stored data is listed in the settings table above, plus `seen_places` in SQLite. Uninstalling or clearing the app's
  data removes all of it.

## Testing

| Level | Location | Examples |
| --- | --- | --- |
| Unit (JVM) | `androidApp/src/test` | `PoiRankerTest`, `KeywordsTest`, `KeywordSuggestionsTest`, `SunTest`, `PostureTest`, `HeadingTest`, `VoiceTest`, `VisitedHistoryTest`, `KeywordWarningTest`, `OverpassSourceTest`, `WikipediaSourceTest`, `SettingsStoreTest`, `MockLocationTest`, `MovementE2ETest`, `LiveWalkE2ETest` |
| Device | `androidApp/src/androidTest` | `NearGemsScreensTest`, `NearGemsNewFeaturesTest`, `AndroidSeenStoreTest` (run on emulators and phones) |

Shared fixtures live in `androidApp/src/sharedTest/.../Fixtures.kt`, with a mock HTTP engine and fake platform
services.

Commands:

```
./gradlew.bat :androidApp:testDebugUnitTest
./gradlew.bat :androidApp:connectedDebugAndroidTest      (ANDROID_SERIAL selects the device)
./gradlew.bat :androidApp:assembleRelease :androidApp:bundleRelease
```

## Known limits

- Public Overpass servers can be busy. Mirrors and retries reduce the impact; a search can still come back short.
- Walking and driving times are straight-line estimates, not routes.
- Compass accuracy depends on the phone's sensors and on magnetic interference.
- The pull-down only works where the card can scroll.
