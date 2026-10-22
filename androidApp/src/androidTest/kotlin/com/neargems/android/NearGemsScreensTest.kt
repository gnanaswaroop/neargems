package com.neargems.android

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.neargems.app.DriveContainer
import com.neargems.support.Fixtures
import com.neargems.ui.NearGemsApp
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
class NearGemsScreensTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val container: DriveContainer = Fixtures.driveContainer(location = Fixtures.CAFE)

    private fun showApp() {
        rule.setContent {
            NearGemsApp(
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

        // Tapping the description opens the whole of it, whether or not the card had to shorten it.
        rule.onNode(hasText("Mo-Fr", substring = true)).performClick()
        rule.onNodeWithText("Close").assertIsDisplayed()

        rule.onNodeWithText("Close").performClick()
        rule.onAllNodesWithText(Fixtures.CAFE_NAME).onFirst().assertIsDisplayed()
    }

    @Test
    fun theSettingsGearOpensSettingsAndTheCheckmarkClosesIt() {
        showApp()

        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("What to suggest").assertIsDisplayed()

        rule.onNodeWithContentDescription("Done").performClick()
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithText("What to suggest").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun theListViewShowsEveryPlaceAndTappingOneOpensItsModal() {
        showApp()
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("List").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Done").performClick()
        rule.waitForIdle()

        rule.onNodeWithText(Fixtures.RESTAURANT_NAME).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(Fixtures.RESTAURANT_NAME).performClick()
        rule.onNodeWithText("Close").assertIsDisplayed()
    }

    @Test
    fun theSearchingScreenShowsBeforeAnyPlaceIsFound() {
        rule.setContent {
            NearGemsApp(
                container = container,
                onStart = {},
                onWatchChanged = {},
                onAllowLocation = {},
                onOpenSettings = {},
            )
        }

        rule.onNodeWithText("Finding places near you").assertIsDisplayed()
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
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("What to suggest").assertIsDisplayed()

        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()

        assertTrue(rule.onAllNodesWithText("What to suggest").fetchSemanticsNodes().isEmpty())
        assertFalse(rule.activity.isFinishing)
    }

    @Test
    fun turningOnTestModeShowsThePresetsAndCoordinateFields() {
        showApp()
        rule.onNodeWithContentDescription("Settings").performClick()

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
