package com.neargem.android

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.neargem.app.DriveContainer
import com.neargem.support.Fixtures
import com.neargem.ui.NearGemApp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the real screens on a device or emulator, with the fake network, GPS and map from the
 * shared test fixtures. Covers what a person taps: the modal, Back, Settings and system back.
 */
@RunWith(AndroidJUnit4::class)
class NearGemScreensTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val container: DriveContainer = Fixtures.driveContainer(location = Fixtures.CAFE)

    private fun showApp() {
        rule.setContent {
            NearGemApp(
                container = container,
                onStart = container::refresh,
                onWatchChanged = {},
                onAllowLocation = {},
                onOpenSettings = {},
            )
        }
        rule.waitUntil(TIMEOUT_MS) {
            rule.onAllNodesWithText(Fixtures.CAFE_NAME).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun readMoreOpensTheFullTextInAModalAndCloseReturnsToTheCard() {
        showApp()

        rule.onNodeWithText("Read more").performClick()
        rule.onNodeWithText("Close").assertIsDisplayed()

        rule.onNodeWithText("Close").performClick()
        rule.onNodeWithText("Read more").assertIsDisplayed()
    }

    @Test
    fun backButtonBringsBackThePlacePassedWithNext() {
        showApp()
        rule.onNodeWithText("Back").assertIsNotEnabled()

        rule.onNodeWithText("Next").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Back").assertIsEnabled().performClick()
        rule.waitForIdle()

        rule.onAllNodesWithText(Fixtures.CAFE_NAME).onFirst().assertIsDisplayed()
    }

    @Test
    fun systemBackOnSettingsGoesToTheSuggestionsInsteadOfLeavingTheApp() {
        showApp()
        rule.onNodeWithText("Settings").performClick()
        rule.onNodeWithText("What to suggest").assertIsDisplayed()

        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()

        assertTrue(rule.onAllNodesWithText("What to suggest").fetchSemanticsNodes().isEmpty())
        assertFalse(rule.activity.isFinishing)
    }

    @Test
    fun turningOnTestModeShowsThePresetsAndCoordinateFields() {
        showApp()
        rule.onNodeWithText("Settings").performClick()

        rule.onNodeWithText(TEST_MODE_LABEL).performScrollTo().performClick()

        rule.onNodeWithText("Oxford Circus").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Latitude").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Use this location").performScrollTo().assertIsDisplayed()
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val TEST_MODE_LABEL = "Use a chosen location instead of GPS"
    }
}
