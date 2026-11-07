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
import com.neargems.app.DriveContainer
import com.neargems.app.PlatformServices
import com.neargems.data.AppSettings
import com.neargems.data.CachedSearch
import com.neargems.data.SearchCache
import com.neargems.platform.SeenStore
import com.neargems.domain.Poi
import com.neargems.domain.PoiCategory
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
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The newest search features on a device: bakeries as a category, and the last search shown at once when the
 * position has not changed. Runs the real screens with fake location, network and speech.
 */
@RunWith(AndroidJUnit4::class)
class FeaturesE2ETest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun container(http: HttpClient, store: MemoryStore = MemoryStore()): DriveContainer = DriveContainer(
        PlatformServices(
            http = http,
            keyValues = store,
            location = FixedLocation(Fixtures.CAFE, emptyFlow()),
            headings = NoHeadings(),
            narrator = SilentNarrator(),
            maps = NoMaps(),
            carConnected = flowOf(false),
            language = "en",
            clock = { 0L },
        ),
    )

    private fun showApp(container: DriveContainer) {
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
    }

    @Test
    fun bakeryIsOfferedInSettingsAndOnByDefault() {
        val container = container(Fixtures.httpClient())
        showApp(container)

        assertTrue(PoiCategory.BAKERY in container.settings.settings.value.categories)
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Bakery").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aBakeryNearbyIsFoundAndShownOnTheCard() {
        val bakery = """{"elements":[{"type":"node","id":7,"lat":51.5002,"lon":-0.1202,
            "tags":{"name":"Hobbs Bakery","shop":"bakery"}}]}"""
        val http = HttpClient(
            MockEngine { request ->
                val body = if (request.url.host == "overpass-api.de") bakery else """{"query":{"geosearch":[]}}"""
                respond(
                    content = body,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        )
        val container = container(http)
        showApp(container)

        rule.waitUntil(TIMEOUT_MS) {
            rule.onAllNodesWithText("Hobbs Bakery").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(PoiCategory.BAKERY, container.state.value.current?.poi?.category)
    }

    @Test
    fun theLastSearchIsShownAtOnceWithoutSearchingAgain() {
        // A search saved by an earlier open, at the same place with the same settings.
        val store = MemoryStore()
        val cafe = Poi(
            id = "osm:node:1",
            name = Fixtures.CAFE_NAME,
            category = PoiCategory.CAFE,
            location = Fixtures.CAFE,
            snippet = "Café",
            interest = 10.0,
        )
        SearchCache(store) { 0L }.save(
            CachedSearch(
                origin = Fixtures.CAFE,
                categories = AppSettings().categories,
                radiusMeters = AppSettings().radiusMeters,
                keywords = emptyList(),
                places = listOf(cafe),
            ),
        )
        // Any network request fails, so the place can only come from the saved search.
        var requests = 0
        val http = HttpClient(
            MockEngine {
                requests++
                respond(content = "", status = HttpStatusCode.InternalServerError)
            },
        )
        val container = container(http, store)
        showApp(container)

        rule.waitUntil(TIMEOUT_MS) {
            rule.onAllNodesWithText(Fixtures.CAFE_NAME).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(0, requests)
    }

    @Test
    fun theAboutPageInSettingsSaysWhyTheAppIsNeededAndShowsTheVersion() {
        val container = container(Fixtures.httpClient())
        rule.setContent {
            NearGemsApp(
                container = container,
                onStart = container::refresh,
                onWatchChanged = {},
                onAllowLocation = {},
                onOpenSettings = {},
                appVersion = "1.1.5 (8)",
            )
        }
        container.setVisible(true)

        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("About").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Version 1.1.5 (8)").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("NearGems suggests interesting places close to you", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun theCardShowsWhenAPlaceWasLastSeen() {
        val cafe = Poi(
            id = "osm:node:1",
            name = Fixtures.CAFE_NAME,
            category = PoiCategory.CAFE,
            location = Fixtures.CAFE,
            snippet = "Café",
            interest = 10.0,
        )
        val seen = object : SeenStore {
            override fun seenIds(): Set<String> = setOf(cafe.id)
            override fun lastSeenTimes(): Map<String, Long> = mapOf(cafe.id to 1_000L)
            override fun markSeen(id: String, name: String, category: String, atMillis: Long, location: com.neargems.domain.LatLon?) = Unit
        }
        val http = HttpClient(
            MockEngine {
                respond(content = "", status = HttpStatusCode.InternalServerError)
            },
        )
        val store = MemoryStore()
        SearchCache(store) { 0L }.save(
            CachedSearch(
                origin = Fixtures.CAFE,
                categories = AppSettings().categories,
                radiusMeters = AppSettings().radiusMeters,
                keywords = emptyList(),
                places = listOf(cafe),
            ),
        )
        val container = DriveContainer(
            PlatformServices(
                http = http,
                keyValues = store,
                location = FixedLocation(Fixtures.CAFE, emptyFlow()),
                headings = NoHeadings(),
                narrator = SilentNarrator(),
                maps = NoMaps(),
                carConnected = flowOf(false),
                language = "en",
                clock = { 0L },
                seen = seen,
                formatDateTime = { "Mon 2 Nov 2026, 10:15" },
            ),
        )
        showApp(container)

        rule.waitUntil(TIMEOUT_MS) {
            rule.onAllNodesWithText("Last seen Mon 2 Nov 2026, 10:15").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
