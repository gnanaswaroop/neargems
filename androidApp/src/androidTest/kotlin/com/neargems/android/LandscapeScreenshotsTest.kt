package com.neargems.android

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.neargems.app.DriveContainer
import com.neargems.app.PlatformServices
import com.neargems.support.Fixtures
import com.neargems.support.FixedLocation
import com.neargems.support.MemoryStore
import com.neargems.support.NoHeadings
import com.neargems.support.NoMaps
import com.neargems.support.SilentNarrator
import com.neargems.ui.NearGemsApp
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Saves the home card and the settings screen in landscape, for each device size, into the same Pictures folder
 * as the portrait screenshots. The activity is turned before the screen is set, because turning it recreates it.
 */
@RunWith(AndroidJUnit4::class)
class LandscapeScreenshotsTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theHomeCardAndSettingsAreSavedInLandscape() {
        // Large screens ignore an app's own orientation request, so the display itself is turned.
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.setOrientationLandscape()
        rule.waitForIdle()

        val container = DriveContainer(
            PlatformServices(
                http = Fixtures.httpClient(),
                keyValues = MemoryStore(),
                location = FixedLocation(Fixtures.CAFE, emptyFlow()),
                headings = NoHeadings(),
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
        rule.waitUntil(TIMEOUT_MS) {
            rule.onAllNodesWithText(Fixtures.CAFE_NAME).fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitForIdle()
        rule.saveScreenshot("home-card-landscape")

        rule.onNodeWithContentDescription("Settings").performClick()
        rule.waitForIdle()
        rule.saveScreenshot("settings-landscape")
        device.setOrientationNatural()
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
