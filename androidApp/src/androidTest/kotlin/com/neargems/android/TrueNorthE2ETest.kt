package com.neargems.android

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.neargems.app.DriveContainer
import com.neargems.app.PlatformServices
import com.neargems.domain.LatLon
import com.neargems.platform.HeadingSource
import com.neargems.support.Fixtures
import com.neargems.support.FixedLocation
import com.neargems.support.MemoryStore
import com.neargems.support.NoMaps
import com.neargems.support.SilentNarrator
import com.neargems.ui.NearGemsApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The phone's compass reads magnetic north. The pointer must use true north, so the local magnetic declination
 * is added to the heading before the N, E, S and W letters are placed.
 */
@RunWith(AndroidJUnit4::class)
class TrueNorthE2ETest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    /** A magnetic heading the test can turn, with a fixed declination for the place. */
    private class MagneticHeading(start: Float, private val declination: Float) : HeadingSource {
        val degrees = MutableStateFlow(start)
        override fun headings(): Flow<Float> = degrees
        override fun magneticDeclination(at: LatLon, atMillis: Long): Float = declination
    }

    @Test
    fun theHeadingIsCorrectedFromMagneticToTrueNorth() {
        // The phone faces 90 degrees magnetic; with 10 degrees of declination it faces 100 degrees true.
        val heading = MagneticHeading(start = 90f, declination = 10f)
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

        rule.waitUntil(TIMEOUT_MS) { container.headingDegrees.value == 100f }
        assertEquals(100f, container.headingDegrees.value)
        // True north is 260 degrees clockwise from the top when the phone faces 100 degrees true.
        rule.waitUntil(TIMEOUT_MS) { northIsAt(260) }
        assertTrue(northIsAt(260))
        rule.waitForIdle()
        rule.saveScreenshot("true-north-declination")
    }

    private fun northIsAt(degrees: Int): Boolean =
        rule.onAllNodes(hasContentDescription("North is $degrees degrees", substring = true))
            .fetchSemanticsNodes().isNotEmpty()

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
