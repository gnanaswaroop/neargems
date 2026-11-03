package com.neargems.android

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.neargems.app.DriveContainer
import com.neargems.app.PlatformServices
import com.neargems.domain.LatLon
import com.neargems.platform.MemorySeenStore
import com.neargems.support.Fixtures
import com.neargems.support.FixedLocation
import com.neargems.support.MemoryStore
import com.neargems.support.NoHeadings
import com.neargems.support.NoMaps
import com.neargems.support.SilentNarrator
import com.neargems.ui.NearGemsApp
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Issues #36, #33, #20 and #38, checked on the device. */
@RunWith(AndroidJUnit4::class)
class IssuesE2ETest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val far = LatLon(53.4808, -2.2426) // Manchester, far outside the search radius

    private fun container(seen: MemorySeenStore = MemorySeenStore(), http: HttpClient = Fixtures.httpClient()) =
        DriveContainer(
            PlatformServices(
                http = http,
                keyValues = MemoryStore(),
                seen = seen,
                location = FixedLocation(Fixtures.CAFE, emptyFlow()),
                headings = NoHeadings(),
                narrator = SilentNarrator(),
                maps = NoMaps(),
                carConnected = flowOf(false),
                language = "en",
                clock = { 0L },
            ),
        )

    private fun show(container: DriveContainer) {
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
    }

    /** #36: the Settings counts match the places seen, and a far-away place opens its card from the list. */
    @Test
    fun settingsCountsThePlacesAndOpensAFarAwayPlaceCard() {
        val seen = MemorySeenStore()
        seen.markSeen("osm:node:9", "Far Bakery", "Bakery", 1_000L, far)
        seen.markSeen("osm:node:1", Fixtures.CAFE_NAME, "Café", 2_000L, Fixtures.CAFE)
        show(container(seen))

        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Discovered 2 · Visited 0").performScrollTo().assertIsDisplayed()
        rule.saveScreenshot("settings-your-places")
        rule.onNodeWithText("Browse all places, near and far ›").performScrollTo().performClick()
        rule.onNodeWithText("Far Bakery").assertIsDisplayed()
        rule.saveScreenshot("places-list")

        rule.onNodeWithText("Far Bakery").performClick()
        rule.waitUntil(TIMEOUT_MS) { rule.onAllNodesWithText("Last seen", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Mark as visited").assertIsDisplayed()
        rule.saveScreenshot("far-place-card")
    }

    /** #36: a place marked visited is listed under Visited, and Back returns to the Settings overview. */
    @Test
    fun aVisitedPlaceIsListedUnderVisitedAndBackReturnsToSettings() {
        show(container())
        rule.onAllNodesWithText("Mark as visited")[0].performClick()
        rule.waitForIdle()

        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Browse all places, near and far ›").performScrollTo().performClick()
        rule.onNodeWithText("Visited (1)").performClick()
        rule.onNodeWithText(Fixtures.CAFE_NAME).assertIsDisplayed()
        rule.saveScreenshot("places-list-visited")

        rule.onNodeWithText("‹ Back to settings").performClick()
        rule.onNodeWithText("Visited 1", substring = true).performScrollTo().assertIsDisplayed()
    }

    /** #36: closing the card of a far-away place returns to the places list. */
    @Test
    fun closingAFarAwayPlaceCardReturnsToThePlacesList() {
        val seen = MemorySeenStore()
        seen.markSeen("osm:node:9", "Far Bakery", "Bakery", 1_000L, far)
        show(container(seen))

        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Browse all places, near and far ›").performScrollTo().performClick()
        rule.onNodeWithText("Far Bakery").performClick()
        rule.waitUntil(TIMEOUT_MS) { rule.onAllNodesWithText("Close").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Close").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Discovered (1)").assertIsDisplayed()
    }

    /** #33 and #20: in landscape the card's text and the Mark as visited action stay on screen. */
    @Test
    fun theLandscapeCardKeepsItsActionsOnScreen() {
        // Large screens ignore an app's own orientation request, so the display itself is turned.
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.setOrientationLandscape()
        try {
            show(container())
            // Saved first, so the layout can be checked even when the assertions below fail.
            rule.saveScreenshot("card-landscape-text-left")
            // The neighbouring card peeks in beside the top one, so the action is found more than once.
            rule.onAllNodesWithText("Mark as visited")[0].assertIsDisplayed()
            rule.onAllNodesWithText(Fixtures.CAFE_NAME)[0].assertIsDisplayed()
        } finally {
            device.setOrientationNatural()
        }
    }

    /**
     * #38: a keyword search whose request never answers must end, so the refresh indicator stops, and the
     * warning says the keyword places are missing while the other places still show.
     */
    @Test
    fun aKeywordSearchThatNeverAnswersStillEnds() {
        val hanging = HttpClient(
            MockEngine { request ->
                val body = (request.body as TextContent).text
                if (body.contains("%22name%22~")) awaitCancellation()
                respond(
                    content = Fixtures.overpassBody,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        )
        val container = container(http = hanging)
        show(container)
        container.updateSettings { it.copy(keywords = "church") }

        // The search gives up after its budget (30 s), so the warning is shown after that, and the wait is longer.
        rule.waitUntil(SEARCH_TIMEOUT_MS) { container.state.value.warning != null }
        rule.waitUntil(TIMEOUT_MS) { !container.state.value.searching && !container.state.value.loading }
        rule.waitForIdle()
        rule.onAllNodesWithText(Fixtures.CAFE_NAME)[0].assertIsDisplayed()
        rule.onNodeWithText("Couldn't fetch places for your keywords", substring = true).assertIsDisplayed()
        rule.saveScreenshot("keyword-warning")
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val SEARCH_TIMEOUT_MS = 60_000L
    }
}
