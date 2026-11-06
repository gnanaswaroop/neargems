package com.neargems.android

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.neargems.app.DriveContainer
import com.neargems.app.PlatformServices
import com.neargems.platform.HeadingSource
import com.neargems.domain.moonPosition
import com.neargems.domain.sunPosition
import com.neargems.support.Fixtures
import com.neargems.support.FixedLocation
import com.neargems.support.MemoryStore
import com.neargems.support.NoMaps
import com.neargems.support.SilentNarrator
import com.neargems.ui.NearGemsApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Issue #44: the direction pointer on each card shows N, E, S and W, and they turn as the phone turns. The
 * labels are drawn on the pointer, so the test reads the north position from the pointer's description.
 */
@RunWith(AndroidJUnit4::class)
class CompassLabelsE2ETest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    /** A phone heading the test can turn. */
    private class TurnableHeading(start: Float) : HeadingSource {
        val degrees = MutableStateFlow(start)
        override fun headings(): Flow<Float> = degrees
    }

    @Test
    fun theNorthLabelTurnsWhenThePhoneTurns() {
        val heading = TurnableHeading(start = 0f)
        val container = DriveContainer(
            PlatformServices(
                http = Fixtures.httpClient(),
                keyValues = MemoryStore(),
                location = FixedLocation(Fixtures.CAFE, emptyFlow()),
                headings = heading,
                narrator = SilentNarrator(),
                maps = NoMaps(),
                carConnected = flowOf(false),
                language = "en",
                clock = { 0L },
            ),
        )
        rule.setContent {
            NearGemsApp(
                container = container,
                onStart = container::refresh,
                onWatchChanged = {},
                onAllowLocation = {},
                onOpenSettings = {},
            )
        }
        container.setVisible(true)
        rule.waitUntil(TIMEOUT_MS) { rule.onAllNodesWithText(Fixtures.CAFE_NAME).fetchSemanticsNodes().isNotEmpty() }

        // Facing north: north is at the top of the pointer.
        rule.waitUntil(TIMEOUT_MS) { northIsAt(0) }
        assertTrue(northIsAt(0))

        // Turn to face east: north is now 270 degrees clockwise from the top, on the left.
        heading.degrees.value = 90f
        rule.waitUntil(TIMEOUT_MS) { northIsAt(270) }
        rule.waitForIdle()
        rule.saveScreenshot("compass-labels-facing-east")
    }

    /**
     * The labels appear with the other directional signals: the arrow to the place, and the sun on the ring when it
     * is up. The clock is set to midday in London, so the sun is above the horizon.
     */
    @Test
    fun theCompassLabelsAreShownWithTheArrowAndTheSun() {
        val heading = TurnableHeading(start = 90f)
        val container = DriveContainer(
            PlatformServices(
                http = Fixtures.httpClient(),
                keyValues = MemoryStore(),
                location = FixedLocation(Fixtures.CAFE, emptyFlow()),
                headings = heading,
                narrator = SilentNarrator(),
                maps = NoMaps(),
                carConnected = flowOf(false),
                language = "en",
                clock = { MIDDAY_IN_LONDON_MILLIS },
            ),
        )
        rule.setContent {
            NearGemsApp(
                container = container,
                onStart = container::refresh,
                onWatchChanged = {},
                onAllowLocation = {},
                onOpenSettings = {},
            )
        }
        container.setVisible(true)
        rule.waitUntil(TIMEOUT_MS) { rule.onAllNodesWithText(Fixtures.CAFE_NAME).fetchSemanticsNodes().isNotEmpty() }
        rule.waitUntil(TIMEOUT_MS) { northIsAt(270) }
        rule.waitUntil(TIMEOUT_MS) {
            rule.onAllNodes(hasContentDescription("Sun is", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitForIdle()
        rule.saveScreenshot("compass-sun")
    }

    /** At night the moon takes the pointer, at its bearing. This picks a time when the sun is down and the moon is up. */
    @Test
    fun theMoonIsShownAtNightWhenTheSunIsDown() {
        val heading = TurnableHeading(start = 90f)
        val night = findNightWithMoonUp()
        val container = DriveContainer(
            PlatformServices(
                http = Fixtures.httpClient(),
                keyValues = MemoryStore(),
                location = FixedLocation(Fixtures.CAFE, emptyFlow()),
                headings = heading,
                narrator = SilentNarrator(),
                maps = NoMaps(),
                carConnected = flowOf(false),
                language = "en",
                clock = { night },
            ),
        )
        rule.setContent {
            NearGemsApp(
                container = container,
                onStart = container::refresh,
                onWatchChanged = {},
                onAllowLocation = {},
                onOpenSettings = {},
            )
        }
        container.setVisible(true)
        rule.waitUntil(TIMEOUT_MS) { rule.onAllNodesWithText(Fixtures.CAFE_NAME).fetchSemanticsNodes().isNotEmpty() }
        rule.waitUntil(TIMEOUT_MS) {
            rule.onAllNodes(hasContentDescription("Moon is", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(northIsAt(270))
        rule.waitForIdle()
        rule.saveScreenshot("compass-moon")
    }

    /** The first hour after midday in June 2026 when the sun is at least 6 degrees below the horizon and the moon is up. */
    private fun findNightWithMoonUp(): Long {
        val start = MIDDAY_IN_LONDON_MILLIS
        for (hour in 0 until 24 * 60) {
            val time = start + hour * 3_600_000L
            val sun = sunPosition(Fixtures.CAFE, time)
            val moon = moonPosition(Fixtures.CAFE, time)
            if (sun.elevationDegrees < -6 && moon.elevationDegrees > 10) return time
        }
        error("no night with the moon up was found")
    }

    private fun northIsAt(degrees: Int): Boolean =
        rule.onAllNodes(hasContentDescription("North is $degrees degrees", substring = true))
            .fetchSemanticsNodes().isNotEmpty()

    private companion object {
        const val TIMEOUT_MS = 10_000L

        /** 12:00 UTC on 21 June 2026, when the sun is high over central London. */
        const val MIDDAY_IN_LONDON_MILLIS = 1_782_033_200_000L
    }
}
