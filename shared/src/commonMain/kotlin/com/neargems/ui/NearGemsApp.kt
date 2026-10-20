package com.neargems.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Settings
import com.neargems.app.DriveContainer
import com.neargems.data.AppSettings
import com.neargems.data.PoiState
import com.neargems.data.PlaceView
import com.neargems.data.ThemeMode
import com.neargems.data.Visit
import com.neargems.domain.LatLon
import com.neargems.domain.Photo
import com.neargems.domain.Poi
import com.neargems.domain.PoiCategory
import com.neargems.domain.RankedPoi
import com.neargems.domain.bearingDegrees
import com.neargems.domain.compassPoint
import com.neargems.domain.driveMinutes
import com.neargems.domain.formatDistance
import com.neargems.domain.summarise
import com.neargems.domain.walkMinutes
import kotlin.math.roundToInt

private val RADIUS_OPTIONS = listOf(500 to "500 m", 1000 to "1 km", 2000 to "2 km", 5000 to "5 km")

/** Width from which the layout switches to two panes (tablets, foldables, landscape phones). */
private val TWO_PANE_MIN_WIDTH = 600.dp

/** Below this height a wide screen is a landscape phone, which gives the place the full width. */
private val WIDE_MAX_HEIGHT = 500.dp

/** Rough characters per line of the snippet body, used to size the collapsed text to the card. */
private const val SNIPPET_CHARS_PER_LINE = 34

/** Share of the card's height given to the mini map when there is room for it. */
private const val MAP_SHARE = 0.25f

/** Height taken by the category, title, distance, buttons and padding, leaving the rest for text. */
private val CARD_CHROME = 230.dp

/** Height of one line of the snippet body. */
private val SNIPPET_LINE_HEIGHT = 25.dp

/** Up to this many places, the swipe cue shows one dot per place. */
private const val MAX_DOTS = 7

private const val HISTORY_SHOWN = 20
private const val ONE_DAY_MILLIS = 24L * 60 * 60 * 1000

/**
 * The whole app UI, shared by every screen size. Phones get one column with the places in a
 * swipeable pager; wider screens get the queue on the left and the places on the right.
 * Nothing scrolls the page itself: the layout fits the screen. Full text opens in a modal.
 * [onStart] runs once on launch (Android asks for location permission there, then refreshes).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NearGemsApp(
    container: DriveContainer,
    onStart: () -> Unit,
    onWatchChanged: (Boolean) -> Unit,
    locationNeeded: Boolean = false,
    locationDenied: Boolean = false,
    onAllowLocation: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    val state by container.state.collectAsState()
    val settings by container.settings.settings.collectAsState()
    val carConnected by container.carConnected.collectAsState()
    val heading by container.headingDegrees.collectAsState()
    val visits by container.visits.collectAsState()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    // The disclosure comes before each system location prompt.
    var disclosed by remember { mutableStateOf(false) }
    PlatformBackHandler(enabled = showSettings) { showSettings = false }

    LaunchedEffect(Unit) { onStart() }

    val actions = Actions(
        onNext = container::next,
        onPrevious = container::previous,
        onSpeak = container::speakCurrent,
        onOpenMap = container::openMap,
        onRefresh = container::refresh,
        onRestoreDismissed = container::restoreDismissed,
        onOpenUrl = container::openUrl,
    )
    val places = listOfNotNull(state.current) + state.upcoming

    // Without location the app has nothing to show. It says what the location is used for, then asks.
    if (locationNeeded && !disclosed) {
        NearGemsTheme(mode = settings.themeMode) {
            LocationDisclosureScreen(onContinue = {
                disclosed = true
                onAllowLocation()
            })
        }
        return
    }
    if (locationDenied) {
        NearGemsTheme(mode = settings.themeMode) {
            LocationRequiredScreen(onAllow = { disclosed = false }, onOpenSettings = onOpenSettings)
        }
        return
    }

    NearGemsTheme(mode = settings.themeMode) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .clipToBounds(),
        ) {
            val twoPane = maxWidth >= TWO_PANE_MIN_WIDTH
            val landscapePhone = twoPane && maxHeight < WIDE_MAX_HEIGHT
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TopBar(
                    showSettings = showSettings,
                    connected = carConnected,
                    simulated = settings.simulateCar,
                    onToggleSettings = { showSettings = !showSettings },
                )

                if (showSettings) {
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        SettingsPanel(settings, visits, container.now(), container::updateSettings, onWatchChanged)
                    }
                } else if (settings.placeView == PlaceView.LIST) {
                    // The whole list, in portrait and landscape alike. Tapping a place opens it in the modal.
                    PlaceListArea(
                        state = state,
                        actions = actions,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                } else if (landscapePhone) {
                    // Landscape phones: the place gets the full width, with its photo beside the details.
                    Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        SuggestionArea(
                            state = state,
                            places = places,
                            from = state.position,
                            heading = heading,
                            large = true,
                            wide = true,
                            actions = actions,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                        state.current?.let { ActionBar(actions, canGoBack = state.hasPrevious) }
                    }
                } else if (twoPane) {
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                    ) {
                        QueuePane(state = state, actions = actions)
                        Column(modifier = Modifier.weight(1f).fillMaxSize()) {
                            SuggestionArea(
                                state = state,
                                places = places,
                                from = state.position,
                                heading = heading,
                                large = true,
                                actions = actions,
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                            )
                            state.current?.let { ActionBar(actions, canGoBack = state.hasPrevious) }
                        }
                    }
                } else {
                    SuggestionArea(
                        state = state,
                        places = places,
                        from = state.position,
                        heading = heading,
                        large = false,
                        actions = actions,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                    state.current?.let { ActionBar(actions, canGoBack = state.hasPrevious) }
                }
            }
        }
    }
}

/**
 * Shown before the system location prompt. Says what location is used for, who receives it and
 * when it is collected, so the user can decide before Android asks.
 */
@Composable
private fun LocationDisclosureScreen(onContinue: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Wordmark()
        Spacer(Modifier.height(32.dp))
        Text(
            "Your location",
            fontFamily = displayFont(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 26.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "NearGems uses your location to find places near you and to show how far away they are and " +
                "which way they are. While the app is open, your position is sent to " +
                "OpenStreetMap and Wikipedia to look up those places. The developer does not receive it.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Only if you turn on car watch does NearGems keep checking your location in the background, " +
                "with a notification, so it can announce places when your car connects.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(50),
        ) { Text("Continue") }
    }
}

/**
 * Shown when the user has refused location. NearGems cannot find anything without it, so the screen
 * says so plainly, offers to ask again, and offers the app's settings for when the system stops asking.
 */
@Composable
private fun LocationRequiredScreen(onAllow: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Wordmark()
        Spacer(Modifier.height(32.dp))
        Text(
            "Location is needed",
            fontFamily = displayFont(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 26.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "NearGems finds the places around you and shows how far away they are and which way to go. " +
                "It cannot be used without your location.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Location permission was not allowed, so the app cannot be used.",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { contentDescription = "Error: location permission not allowed" },
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onAllow,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(50),
        ) { Text("Allow location") }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = RoundedCornerShape(50),
        ) { Text("Open app settings") }
    }
}

/** Callbacks the suggestion views call. Grouped so the composables stay short. */
private class Actions(
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
    val onSpeak: () -> Unit,
    val onOpenMap: (Poi) -> Unit,
    val onRefresh: () -> Unit,
    val onRestoreDismissed: () -> Unit,
    val onOpenUrl: (String) -> Unit,
)

/** Title row: the wordmark, the connection icon (car, with a status dot), and Settings. */
@Composable
private fun TopBar(showSettings: Boolean, connected: Boolean, simulated: Boolean, onToggleSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Wordmark(modifier = Modifier.weight(1f))
        ConnectionIcon(connected = connected, simulated = simulated)
        Spacer(Modifier.width(4.dp))
        // A plain icon, like the car beside it: no border, so it reads as an icon rather than a button.
        IconButton(onClick = onToggleSettings, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = if (showSettings) Icons.Outlined.Check else Icons.Outlined.Settings,
                contentDescription = if (showSettings) "Done" else "Settings",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

/**
 * The car, from the Material Symbols icon set: filled and teal when a car display is connected,
 * outlined and grey when it is not. The status dot is kept so the state reads at a glance.
 */
@Composable
private fun ConnectionIcon(connected: Boolean, simulated: Boolean) {
    val label = when {
        connected && simulated -> "Car connected (simulated)"
        connected -> "Connected to a car display"
        else -> "Not connected to a car"
    }
    val tint = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(modifier = Modifier.size(32.dp).semantics { contentDescription = label }) {
        Icon(
            imageVector = if (connected) Icons.Filled.DirectionsCar else Icons.Outlined.DirectionsCar,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(28.dp).align(Alignment.BottomStart),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(9.dp)
                .clip(CircleShape)
                .background(if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                .border(1.5.dp, MaterialTheme.colorScheme.background, CircleShape),
        )
    }
}

/**
 * The places as a list, for choosing one rather than swiping through cards. Tapping a row opens the
 * place in the same modal as a card, with its photo viewer. Read aloud, Next and Back stay pinned below.
 */
@Composable
private fun PlaceListArea(state: PoiState, actions: Actions, modifier: Modifier) {
    val queue = listOfNotNull(state.current) + state.upcoming
    var openPlace by remember { mutableStateOf<RankedPoi?>(null) }
    var openPhoto by remember { mutableStateOf<Photo?>(null) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (queue.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    state.message ?: "Nothing to suggest right now.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(queue, key = { it.poi.id }) { ranked ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) { openPlace = ranked },
                    ) {
                        QueueRow(ranked, selected = ranked == state.current)
                    }
                }
            }
        }
        state.current?.let { ActionBar(actions, canGoBack = state.hasPrevious) }
    }
    openPlace?.let { ranked ->
        PlaceDetailDialog(ranked.poi, actions, onPhotoClick = { openPhoto = ranked.poi.photo }) { openPlace = null }
    }
    openPhoto?.let { photo ->
        PhotoViewer(photo, openPlace?.poi?.name.orEmpty(), actions) { openPhoto = null }
    }
}

/** Tablet left pane: the top pick and the rest of the queue. */
@Composable
private fun QueuePane(state: PoiState, actions: Actions) {
    val queue = listOfNotNull(state.current) + state.upcoming
    Column(
        modifier = Modifier.width(340.dp).fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Nearby",
            style = MaterialTheme.typography.titleMedium,
            fontFamily = displayFont(),
            modifier = Modifier.semantics { heading() },
        )
        if (queue.isEmpty()) {
            Text(
                state.message ?: "Nothing to suggest right now.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(queue, key = { it.poi.id }) { ranked ->
                    QueueRow(ranked, selected = ranked == state.current)
                }
            }
        }
    }
}

@Composable
private fun QueueRow(ranked: RankedPoi, selected: Boolean) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(ranked.poi.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                ranked.poi.category.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            formatDistance(ranked.distanceMeters),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The places, a loading message while searching, or the empty state when nothing is left. */
@Composable
private fun SuggestionArea(
    state: PoiState,
    places: List<RankedPoi>,
    from: LatLon?,
    heading: Float?,
    large: Boolean,
    wide: Boolean = false,
    actions: Actions,
    modifier: Modifier,
) {
    when {
        places.isNotEmpty() -> SuggestionPager(
            places = places,
            previous = state.previous,
            from = from,
            heading = heading,
            large = large,
            wide = wide,
            actions = actions,
            modifier = modifier,
        )
        state.loading -> Box(modifier = modifier) {
            Text(
                "Searching nearby…",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        else -> Box(modifier = modifier, contentAlignment = Alignment.Center) {
            EmptyState(message = state.message, actions = actions)
        }
    }
}

/**
 * The places as swipeable cards. Swipe right to left to see the next place, and right to go back to
 * the last one passed with Next. Each neighbour peeks in from its edge, and a cue shows where you
 * are in the list. The top pick returns to the first card whenever it changes.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SuggestionPager(
    places: List<RankedPoi>,
    previous: RankedPoi?,
    from: LatLon?,
    heading: Float?,
    large: Boolean,
    wide: Boolean,
    actions: Actions,
    modifier: Modifier,
) {
    // The place Back brings back is the page on the left, so a swipe right goes back as a swipe left goes on.
    val pages = listOfNotNull(previous) + places
    val offset = pages.size - places.size
    val pagerState = rememberPagerState(initialPage = offset, pageCount = { pages.size })
    val topId = places.firstOrNull()?.poi?.id
    LaunchedEffect(topId) { pagerState.scrollToPage(offset) }
    // Landing on the left-hand page means the user swiped back to it, so bring that place back.
    LaunchedEffect(pagerState.settledPage) {
        if (offset == 1 && pagerState.settledPage == 0) actions.onPrevious()
    }

    Column(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(
                start = if (offset == 1) 28.dp else 0.dp,
                end = if (places.size > 1) 28.dp else 0.dp,
            ),
            pageSpacing = 12.dp,
            key = { page -> pages[page].poi.id },
        ) { page ->
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                SuggestionCard(
                    ranked = pages[page],
                    large = large,
                    wide = wide,
                    from = from,
                    heading = heading,
                    availableHeight = maxHeight,
                    actions = actions,
                )
            }
        }
        SwipeCue(
            page = (pagerState.currentPage - offset).coerceAtLeast(0),
            count = places.size,
            back = offset == 1,
        )
    }
}

/** Dots (or a count) and a hint, so it is clear that there are more places to swipe through or back to. */
@Composable
private fun SwipeCue(page: Int, count: Int, back: Boolean) {
    if (count <= 1 && !back) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (back) {
            Text(
                if (count > 1) "‹ back  ·  " else "‹ back",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (count <= MAX_DOTS) {
            repeat(count) { index ->
                val selected = index == page
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (selected) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        ),
                )
            }
            Spacer(Modifier.width(8.dp))
        }
        Text(
            if (count > 1) "${page + 1} of $count · swipe for more" else "",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One place. Photo and map shrink, and then disappear, as the available height drops, so a small
 * phone fits the card without scrolling. The full text opens in a modal instead.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuggestionCard(
    ranked: RankedPoi,
    large: Boolean,
    wide: Boolean,
    from: LatLon?,
    heading: Float?,
    availableHeight: Dp,
    actions: Actions,
) {
    val poi = ranked.poi
    val shape = RoundedCornerShape(20.dp)
    // Remembered per place, so the modal stays open across GPS updates and starts closed for a new place.
    var detailOpen by rememberSaveable(poi.id) { mutableStateOf(false) }
    var photoOpen by rememberSaveable(poi.id) { mutableStateOf(false) }
    val h = availableHeight
    // The map takes a share of whatever height the card has, and the text gets the rest.
    // Landscape phones and short windows get a smaller photo and map, so the text still fits.
    val photoHeight = when {
        h >= 700.dp -> if (large) 220.dp else 200.dp
        h >= 560.dp -> 150.dp
        h >= 460.dp -> 96.dp
        else -> 0.dp
    }
    val mapHeight = when {
        large && h >= 560.dp -> 150.dp
        h >= 340.dp -> (h * MAP_SHARE).coerceIn(80.dp, 260.dp)
        else -> 0.dp
    }
    val textSpace = h - photoHeight - mapHeight - CARD_CHROME
    val foldLines = (textSpace / SNIPPET_LINE_HEIGHT).toInt().coerceIn(2, 12)
    // Only whole sentences that fit the space the card has; the full text is one tap away.
    val collapsed = summarise(poi.snippet, maxChars = foldLines * SNIPPET_CHARS_PER_LINE)

    // If the text still does not fit a very short window, the card scrolls rather than cutting it off.
    if (wide) {
        WideSuggestionCard(ranked, from, heading, h, actions, onPhotoClick = { photoOpen = true }, onDetailClick = { detailOpen = true })
    } else {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(if (large) 20.dp else 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(if (large) 10.dp else 8.dp),
    ) {
        if (photoHeight > 0.dp) {
            poi.photo?.let { photo ->
                PhotoBlock(photo, poi.name, photoHeight, actions, onPhotoClick = { photoOpen = true })
            }
        }
        Text(
            buildString {
                append(poi.category.label.uppercase())
                if (large) append(" · TOP PICK")
            },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            poi.name,
            fontFamily = displayFont(),
            fontWeight = FontWeight.SemiBold,
            fontSize = if (large) 40.sp else 26.sp,
            lineHeight = if (large) 46.sp else 30.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        // The opening sentences that fit. Tapping the text opens the whole of it.
        Text(
            collapsed,
            style = MaterialTheme.typography.bodyLarge,
            fontSize = 17.sp,
            lineHeight = 25.sp,
            maxLines = foldLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { role = Role.Button }.clickable { detailOpen = true },
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        )
        if (collapsed != poi.snippet) {
            Text(
                "Read more",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { role = Role.Button }.clickable { detailOpen = true },
            )
        }
        DistanceDetails(ranked, from, heading)
        if (mapHeight > 0.dp) {
            from?.let { MiniMap(from = it, to = poi.location, height = mapHeight, onClick = { actions.onOpenMap(poi) }) }
        }
    }
    }
    if (detailOpen) {
        PlaceDetailDialog(poi, actions, onPhotoClick = { photoOpen = true }) { detailOpen = false }
    }
    if (photoOpen) {
        poi.photo?.let { photo -> PhotoViewer(photo, poi.name, actions) { photoOpen = false } }
    }
}

/**
 * Landscape phones: the photo on the left and the whole place on the right, so the width is used
 * for information rather than a list. The text is not folded here; the right side scrolls if needed.
 */
@Composable
private fun WideSuggestionCard(
    ranked: RankedPoi,
    from: LatLon?,
    heading: Float?,
    availableHeight: Dp,
    actions: Actions,
    onPhotoClick: () -> Unit,
    onDetailClick: () -> Unit,
) {
    val poi = ranked.poi
    Row(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
            .padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        poi.photo?.let { photo ->
            Column(modifier = Modifier.weight(1f)) {
                PhotoBlock(photo, poi.name, (availableHeight - 56.dp).coerceAtLeast(120.dp), actions, onPhotoClick)
            }
        }
        Column(
            modifier = Modifier.weight(1.3f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                poi.category.label.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                poi.name,
                fontFamily = displayFont(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 40.sp,
                lineHeight = 46.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                poi.snippet,
                style = MaterialTheme.typography.bodyLarge,
                fontSize = 17.sp,
                lineHeight = 25.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                modifier = Modifier.semantics { role = Role.Button }.clickable(onClick = onDetailClick),
            )
            DistanceDetails(ranked, from, heading)
            from?.let { MiniMap(from = it, to = poi.location, height = 120.dp, onClick = { actions.onOpenMap(poi) }) }
        }
    }
}

/** The photo at nearly full screen, for looking at detail. Tapping the credit still opens its licence page. */
@Composable
private fun PhotoViewer(photo: Photo, name: String, actions: Actions, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val shape = RoundedCornerShape(20.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.86f)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SubcomposeAsyncImage(
                model = photo.imageUrl,
                contentDescription = "Photo of $name",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().weight(1f),
                loading = { Text("Loading photo…", style = MaterialTheme.typography.labelSmall) },
                error = { Text("Photo unavailable", style = MaterialTheme.typography.labelSmall) },
            )
            Text(
                "Photo: Wikipedia · licence on the file page",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { actions.onOpenUrl(photo.fileUrl) },
            )
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(50),
            ) { Text("Close") }
        }
    }
}

/**
 * The whole place in a modal over the list: the photo, the full text, and a way back. Closing it
 * leaves the list where it was, so swiping still works afterwards.
 */
@Composable
private fun PlaceDetailDialog(poi: Poi, actions: Actions, onPhotoClick: () -> Unit, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val shape = RoundedCornerShape(20.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 640.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            poi.photo?.let { photo -> PhotoBlock(photo, poi.name, 220.dp, actions, onPhotoClick = onPhotoClick) }
            Text(
                poi.category.label.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                poi.name,
                fontFamily = displayFont(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 30.sp,
                lineHeight = 36.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                poi.snippet,
                style = MaterialTheme.typography.bodyLarge,
                fontSize = 17.sp,
                lineHeight = 25.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
            )
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(50),
            ) { Text("Close") }
        }
    }
}

/**
 * The actions, pinned to the bottom of the screen. Sitting in the same place whatever the card
 * holds keeps the buttons where the thumb expects them.
 */
@Composable
private fun ActionBar(actions: Actions, canGoBack: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
        OutlinedButton(
            onClick = actions.onPrevious,
            enabled = canGoBack,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            shape = RoundedCornerShape(50),
        ) { Text("Back") }
        Button(
            onClick = actions.onSpeak,
            modifier = Modifier.weight(1.6f).heightIn(min = 48.dp),
            shape = RoundedCornerShape(50),
        ) { Text("Read aloud") }
        OutlinedButton(
            onClick = actions.onNext,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            shape = RoundedCornerShape(50),
        ) { Text("Next") }
    }
}

/**
 * Distance from where the device is now. [RankedPoi.distanceMeters] is recomputed on every
 * position fix, so this line updates as the car or person moves.
 */
@Composable
private fun DistanceDetails(ranked: RankedPoi, from: LatLon?, heading: Float?) {
    val bearing = from?.let { bearingDegrees(it, ranked.poi.location) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (bearing != null && heading != null) {
            CompassPointer(relativeDegrees = (bearing - heading).toFloat())
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "${formatDistance(ranked.distanceMeters)} away" + bearing?.let { " · ${compassPoint(it)}" }.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "About ${walkMinutes(ranked.distanceMeters)} min walk · ${driveMinutes(ranked.distanceMeters)} min drive",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Arrow that points at the place relative to the top of the phone: straight up means the place is
 * dead ahead, pointing right means it is to your right, and so on. It turns as you turn.
 */
@Composable
private fun CompassPointer(relativeDegrees: Float) {
    val ring = MaterialTheme.colorScheme.outlineVariant
    val accent = MaterialTheme.colorScheme.primary
    val turn = ((relativeDegrees % 360) + 360) % 360
    Canvas(
        modifier = Modifier
            .size(52.dp)
            .semantics { contentDescription = "Direction pointer, place is ${turn.roundToInt()} degrees clockwise from your heading" },
    ) {
        drawCircle(color = ring, radius = size.minDimension / 2 - 2f, style = Stroke(width = 3f))
        val arrow = Path().apply {
            moveTo(size.width / 2, size.height * 0.14f)
            lineTo(size.width * 0.78f, size.height * 0.80f)
            lineTo(size.width / 2, size.height * 0.64f)
            lineTo(size.width * 0.22f, size.height * 0.80f)
            close()
        }
        rotate(degrees = turn, pivot = center) {
            drawPath(arrow, color = accent)
        }
    }
}

/**
 * Lead image from Wikipedia, loaded from its public URL at runtime and never bundled. The credit
 * line opens the file page, where the image's licence is listed.
 */
@Composable
private fun PhotoBlock(photo: Photo, name: String, height: Dp, actions: Actions, onPhotoClick: () -> Unit) {
    val placeholder: @Composable (String) -> Unit = { label ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    SubcomposeAsyncImage(
        model = photo.imageUrl,
        contentDescription = "Photo of $name",
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onPhotoClick),
        loading = { placeholder("Loading photo…") },
        error = { placeholder("Photo unavailable") },
    )
    Text(
        "Photo: Wikipedia · licence on the file page",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.clickable { actions.onOpenUrl(photo.fileUrl) },
    )
}

@Composable
private fun EmptyState(message: String?, actions: Actions) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .border(2.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
        )
        Text(
            message ?: "Nothing to suggest right now.",
            fontFamily = displayFont(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp,
            textAlign = TextAlign.Center,
        )
        OutlinedButton(
            onClick = actions.onRefresh,
            modifier = Modifier.heightIn(min = 48.dp),
            shape = RoundedCornerShape(50),
        ) { Text("Refresh") }
        TextButton(onClick = actions.onRestoreDismissed, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Show dismissed places again")
        }
    }
}

@Composable
private fun SettingsPanel(
    settings: AppSettings,
    visits: List<Visit>,
    now: Long,
    onSettings: ((AppSettings) -> AppSettings) -> Unit,
    onWatchChanged: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("What to suggest")
        PoiCategory.entries.forEach { category ->
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = category in settings.categories,
                    onCheckedChange = { on ->
                        onSettings { s ->
                            s.copy(categories = if (on) s.categories + category else s.categories - category)
                        }
                    },
                )
                Text(category.label, style = MaterialTheme.typography.bodyLarge)
            }
        }

        SectionTitle("Search radius")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RADIUS_OPTIONS.forEach { (meters, label) ->
                FilterChip(
                    selected = settings.radiusMeters == meters,
                    onClick = { onSettings { it.copy(radiusMeters = meters) } },
                    label = { Text(label) },
                )
            }
        }

        SectionTitle("Appearance")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = settings.themeMode == mode,
                    onClick = { onSettings { it.copy(themeMode = mode) } },
                    label = { Text(mode.label()) },
                )
            }
        }

        SectionTitle("Places")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlaceView.entries.forEach { view ->
                FilterChip(
                    selected = settings.placeView == view,
                    onClick = { onSettings { it.copy(placeView = view) } },
                    label = { Text(if (view == PlaceView.CARDS) "One card at a time" else "List") },
                )
            }
        }

        SectionTitle("Visited")
        if (visits.isEmpty()) {
            Text(
                "Places you get within 50 m of appear here. This history stays on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            visits.take(HISTORY_SHOWN).forEach { visit ->
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(visit.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            visit.categoryLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        visitedLabel(visit.atMillis, now),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        SectionTitle("Car")
        SwitchRow(
            label = "Read a suggestion aloud when a car connects",
            checked = settings.announceOnCar,
            onChange = { on -> onSettings { it.copy(announceOnCar = on) } },
        )
        SwitchRow(
            label = "Keep watching for a car in the background (Android)",
            checked = settings.watchInBackground,
            onChange = { on ->
                onSettings { it.copy(watchInBackground = on) }
                onWatchChanged(on)
            },
        )
        SwitchRow(
            label = "Testing: pretend a car is connected",
            checked = settings.simulateCar,
            onChange = { on -> onSettings { it.copy(simulateCar = on) } },
        )

        SectionTitle("Testing location")
        SwitchRow(
            label = "Use a chosen location instead of GPS",
            checked = settings.testLocation != null,
            onChange = { on -> onSettings { it.copy(testLocation = if (on) it.testLocation ?: TEST_PRESETS.first().second else null) } },
        )
        settings.testLocation?.let { location ->
            TestLocationEditor(location) { chosen -> onSettings { it.copy(testLocation = chosen) } }
        }
    }
}

/** Places a test location can be set to, for trying the suggestions without walking anywhere. */
private val TEST_PRESETS = listOf(
    "Trafalgar Square" to LatLon(51.5080, -0.1281),
    "Oxford Circus" to LatLon(51.5152, -0.1419),
    "Tower Bridge" to LatLon(51.5055, -0.0754),
)

/** Presets and typed coordinates for the test location. Suggestions update as soon as one is set. */
@Composable
private fun TestLocationEditor(location: LatLon, onLocation: (LatLon) -> Unit) {
    var latitude by remember(location) { mutableStateOf(location.lat.toString()) }
    var longitude by remember(location) { mutableStateOf(location.lon.toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TEST_PRESETS.forEach { (name, point) ->
                FilterChip(
                    selected = point == location,
                    onClick = { onLocation(point) },
                    label = { Text(name) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = latitude,
                onValueChange = { latitude = it },
                label = { Text("Latitude") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = longitude,
                onValueChange = { longitude = it },
                label = { Text("Longitude") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }
        error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Button(
            onClick = {
                val lat = latitude.trim().toDoubleOrNull()
                val lon = longitude.trim().toDoubleOrNull()
                if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
                    error = null
                    onLocation(LatLon(lat, lon))
                } else {
                    error = "Enter a latitude from -90 to 90 and a longitude from -180 to 180."
                }
            },
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text("Use this location") }
    }
}

/** "Today", "Yesterday" or "N days ago", for the visit history. */
private fun visitedLabel(atMillis: Long, now: Long): String {
    val days = ((now - atMillis) / ONE_DAY_MILLIS).toInt()
    return when {
        days <= 0 -> "Today"
        days == 1 -> "Yesterday"
        else -> "$days days ago"
    }
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontFamily = displayFont(),
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    // The whole row is the switch, so a tap anywhere on the label works too.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}
