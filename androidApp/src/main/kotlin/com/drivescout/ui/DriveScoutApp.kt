package com.drivescout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivescout.app.DriveContainer
import com.drivescout.data.AppSettings
import com.drivescout.data.PoiState
import com.drivescout.domain.Poi
import com.drivescout.domain.PoiCategory
import com.drivescout.domain.RankedPoi
import com.drivescout.domain.formatDistance

private val RADIUS_OPTIONS = listOf(500 to "500 m", 1000 to "1 km", 2000 to "2 km", 5000 to "5 km")

/** Width from which the layout switches to two panes (tablets, foldables, landscape phones). */
private val TWO_PANE_MIN_WIDTH = 600.dp

/**
 * The whole app UI, shared by every screen size. Phones get one column; wider screens get the
 * queue on the left and the suggestion (or settings) on the right.
 * [onStart] runs once on launch (Android asks for location permission there, then refreshes).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DriveScoutApp(
    container: DriveContainer,
    onStart: () -> Unit,
    onWatchChanged: (Boolean) -> Unit,
) {
    val state by container.state.collectAsState()
    val settings by container.settings.settings.collectAsState()
    val carConnected by container.carConnected.collectAsState()
    var showSettings by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { onStart() }

    val actions = Actions(
        onNext = container::next,
        onSpeak = container::speakCurrent,
        onOpenMap = container::openMap,
        onRefresh = container::refresh,
        onRestoreDismissed = container::restoreDismissed,
    )

    DriveScoutTheme {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            val twoPane = maxWidth >= TWO_PANE_MIN_WIDTH
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TopBar(showSettings = showSettings, onToggleSettings = { showSettings = !showSettings })
                ConnectionStatus(connected = carConnected, simulated = settings.simulateCar)

                if (twoPane) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                    ) {
                        QueuePane(state = state, actions = actions)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            if (showSettings) {
                                SettingsPanel(settings, container::updateSettings, onWatchChanged)
                            } else {
                                SuggestionView(state = state, actions = actions, large = true)
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        if (showSettings) {
                            SettingsPanel(settings, container::updateSettings, onWatchChanged)
                        } else {
                            SuggestionView(state = state, actions = actions, large = false)
                        }
                    }
                }
            }
        }
    }
}

/** Callbacks the suggestion views call. Grouped so the composables stay short. */
private class Actions(
    val onNext: () -> Unit,
    val onSpeak: () -> Unit,
    val onOpenMap: (Poi) -> Unit,
    val onRefresh: () -> Unit,
    val onRestoreDismissed: () -> Unit,
)

@Composable
private fun TopBar(showSettings: Boolean, onToggleSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "DriveScout",
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp,
            color = MaterialTheme.colorScheme.onBackground,
        )
        TextButton(onClick = onToggleSettings) {
            Text(if (showSettings) "Done" else "Settings", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ConnectionStatus(connected: Boolean, simulated: Boolean) {
    val label = when {
        connected && simulated -> "Car connected (simulated)"
        connected -> "Connected to a car display"
        else -> "Not connected to a car"
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                ),
        )
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Text("Nearby", style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Serif)
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

/** The top pick card, a message while loading, or the empty state when nothing is left. */
@Composable
private fun SuggestionView(state: PoiState, actions: Actions, large: Boolean) {
    val current = state.current
    when {
        current != null -> SuggestionCard(
            ranked = current,
            queued = state.queued,
            large = large,
            actions = actions,
        )
        state.loading -> Text(
            "Searching nearby…",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> EmptyState(message = state.message, actions = actions)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuggestionCard(ranked: RankedPoi, queued: Int, large: Boolean, actions: Actions) {
    val poi = ranked.poi
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val kicker = buildString {
            append(poi.category.label.uppercase())
            append(" · ")
            append(formatDistance(ranked.distanceMeters))
            if (large) append(" · TOP PICK")
        }
        Text(
            kicker,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            poi.name,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
            fontSize = if (large) 40.sp else 30.sp,
            lineHeight = if (large) 46.sp else 36.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            poi.snippet,
            style = MaterialTheme.typography.bodyLarge,
            fontSize = 17.sp,
            lineHeight = 25.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = actions.onSpeak,
                modifier = Modifier.heightIn(min = 48.dp),
                shape = RoundedCornerShape(50),
            ) { Text("Read aloud") }
            OutlinedButton(
                onClick = actions.onNext,
                modifier = Modifier.heightIn(min = 48.dp),
                shape = RoundedCornerShape(50),
            ) { Text("Next") }
            TextButton(
                onClick = { actions.onOpenMap(poi) },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("Map") }
        }
        if (queued > 0) {
            Text(
                "$queued more nearby",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
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
            fontFamily = FontFamily.Serif,
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
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
