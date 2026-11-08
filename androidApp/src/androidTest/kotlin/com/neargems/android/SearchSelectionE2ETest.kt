package com.neargems.android

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.neargems.app.DriveContainer
import com.neargems.app.PlatformServices
import com.neargems.domain.PoiCategory
import com.neargems.support.FixedLocation
import com.neargems.support.Fixtures
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
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URLDecoder

/**
 * The search selections a user makes on screen: the categories they want suggestions for, and the keyword tags.
 * The fake Overpass server answers each query with the places its category or keyword asks for, so a change of
 * selection shows up in the places the way it would with the real server. A keyword adds its places next to the
 * chosen categories; it does not replace them.
 */
@RunWith(AndroidJUnit4::class)
class SearchSelectionE2ETest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    /** A place in the fake OpenStreetMap data: one tag, which decides which categories or keywords find it. */
    private data class FakePlace(val id: Long, val name: String, val lat: Double, val lon: Double, val key: String, val value: String)

    private val places = listOf(
        FakePlace(1, Fixtures.CAFE_NAME, 51.5000, -0.1200, "amenity", "cafe"),
        FakePlace(2, Fixtures.RESTAURANT_NAME, 51.5045, -0.1200, "amenity", "restaurant"),
        FakePlace(3, BAKERY_NAME, 51.5002, -0.1202, "shop", "bakery"),
        // A place of worship is not one of the categories, so it is found only by a keyword such as "church".
        FakePlace(4, CHURCH_NAME, 51.5010, -0.1210, "amenity", "place_of_worship"),
    )

    /**
     * Answers the query the way Overpass would: a category query returns the places with its amenity or shop; a
     * keyword query returns the named places, and the app keeps the ones whose name matches the keyword.
     */
    private fun overpass(): HttpClient = HttpClient(
        MockEngine { request ->
            val form = (request.body as? TextContent)?.text
            val body = if (form == null || !form.startsWith("data=")) {
                // The Wikipedia lookups: nothing found.
                """{"query":{"geosearch":[]}}"""
            } else {
                elementsFor(URLDecoder.decode(form.removePrefix("data="), "UTF-8"))
            }
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        },
    )

    private fun elementsFor(query: String): String {
        val found = if ("[\"name\"~" in query) {
            // Overpass returns only the names that match the keyword pattern, case-insensitively.
            val alternatives = Regex("""nwr\["name"~"\((.*)\)",i\]""").find(query)?.groupValues?.get(1)?.split("|").orEmpty()
            places.filter { p -> alternatives.any { p.name.lowercase().contains(it.lowercase()) } }
        } else {
            val amenities = groupOf(query, "amenity")
            val shops = groupOf(query, "shop")
            places.filter { (it.key == "amenity" && it.value in amenities) || (it.key == "shop" && it.value in shops) }
        }
        return found.joinToString(prefix = """{"elements":[""", postfix = "]}") { p ->
            """{"type":"node","id":${p.id},"lat":${p.lat},"lon":${p.lon},"tags":{"name":"${p.name}","${p.key}":"${p.value}"}}"""
        }
    }

    /** The values listed in a query's `["key"~"^(a|b)$"]` filter. */
    private fun groupOf(query: String, key: String): List<String> =
        Regex("""\["$key"~"\^\(([^)]*)\)\$"\]""").find(query)?.groupValues?.get(1)?.split("|").orEmpty()

    private fun showApp(): DriveContainer {
        val container = DriveContainer(
            PlatformServices(
                http = overpass(),
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
        return container
    }

    /** Opens settings, runs [change], and returns to the suggestions. */
    private fun inSettings(change: () -> Unit) {
        rule.onNodeWithContentDescription("Settings").performClick()
        change()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Done").performClick()
    }

    /** Switches to the list view, which shows every place found rather than one card. */
    private fun showList() = inSettings { rule.onNodeWithText("List").performScrollTo().performClick() }

    private fun shown(name: String): Boolean = rule.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun aCategoryTurnedOffIsNoLongerSuggested() {
        showApp()
        showList()
        rule.waitUntil(TIMEOUT_MS) { shown(Fixtures.CAFE_NAME) }

        inSettings { rule.onNodeWithText(PoiCategory.CAFE.label).performScrollTo().performClick() }

        rule.waitUntil(TIMEOUT_MS) { !shown(Fixtures.CAFE_NAME) }
        assertTrue("the bakery is still suggested", shown(BAKERY_NAME))
    }

    @Test
    fun onlyTheCategoriesChosenAreSuggested() {
        showApp()
        showList()
        rule.waitUntil(TIMEOUT_MS) { shown(Fixtures.CAFE_NAME) }

        // Keep only Bakery: the cafe and the restaurant must both go.
        inSettings {
            rule.onNodeWithText(PoiCategory.CAFE.label).performScrollTo().performClick()
            rule.onNodeWithText(PoiCategory.RESTAURANT.label).performScrollTo().performClick()
        }

        rule.waitUntil(TIMEOUT_MS) { !shown(Fixtures.CAFE_NAME) && !shown(Fixtures.RESTAURANT_NAME) }
        assertTrue("the bakery should be the one place left", shown(BAKERY_NAME))
    }

    @Test
    fun aCategoryTurnedBackOnComesBack() {
        showApp()
        showList()
        rule.waitUntil(TIMEOUT_MS) { shown(Fixtures.CAFE_NAME) }

        inSettings { rule.onNodeWithText(PoiCategory.CAFE.label).performScrollTo().performClick() }
        rule.waitUntil(TIMEOUT_MS) { !shown(Fixtures.CAFE_NAME) }

        inSettings { rule.onNodeWithText(PoiCategory.CAFE.label).performScrollTo().performClick() }
        rule.waitUntil(TIMEOUT_MS) { shown(Fixtures.CAFE_NAME) }
    }

    @Test
    fun aKeywordAddsItsPlacesNextToTheChosenCategories() {
        val container = showApp()
        showList()
        rule.waitUntil(TIMEOUT_MS) { shown(Fixtures.CAFE_NAME) }

        inSettings { rule.onNodeWithText("Add a keyword").performScrollTo().performTextInput("church,") }

        rule.waitUntil(TIMEOUT_MS) { shown(CHURCH_NAME) }
        assertTrue("the cafe should still be suggested", shown(Fixtures.CAFE_NAME))
        assertEquals("church", container.settings.settings.value.keywords)
    }

    @Test
    fun removingAKeywordTagTakesItsPlacesAwayAndKeepsTheCategories() {
        val container = showApp()
        showList()
        rule.waitUntil(TIMEOUT_MS) { shown(Fixtures.CAFE_NAME) }

        inSettings { rule.onNodeWithText("Add a keyword").performScrollTo().performTextInput("church,") }
        rule.waitUntil(TIMEOUT_MS) { shown(CHURCH_NAME) }

        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithContentDescription("Remove church").performClick()
        rule.onNodeWithContentDescription("Done").performClick()

        rule.waitUntil(TIMEOUT_MS) { !shown(CHURCH_NAME) }
        assertTrue("the cafe should still be suggested", shown(Fixtures.CAFE_NAME))
        assertEquals("", container.settings.settings.value.keywords)
    }

    @Test
    fun aKeywordThatMatchesNoPlaceAddsNothing() {
        val container = showApp()
        showList()
        rule.waitUntil(TIMEOUT_MS) { shown(Fixtures.CAFE_NAME) }

        inSettings { rule.onNodeWithText("Add a keyword").performScrollTo().performTextInput("zoo,") }

        // Wait for the search to finish, then check nothing was added.
        rule.waitUntil(TIMEOUT_MS) { !container.state.value.loading }
        rule.waitForIdle()
        assertTrue("the cafe should still be suggested", shown(Fixtures.CAFE_NAME))
        assertTrue("the bakery should still be suggested", shown(BAKERY_NAME))
        assertTrue("a place with no match should not be added", !shown(CHURCH_NAME))
    }

    @Test
    fun aMatchingIdeaAddsItsKeywordTag() {
        showApp()
        rule.waitUntil(TIMEOUT_MS) { shown(Fixtures.CAFE_NAME) }

        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Add a keyword").performScrollTo().performTextInput("temp")
        rule.waitForIdle()
        rule.onNodeWithText("Matching ideas").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("temple").performScrollTo().performClick()
        rule.waitForIdle()

        rule.onAllNodesWithContentDescription("Remove temple").assertCountEquals(1)
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val BAKERY_NAME = "Hobbs Bakery"
        const val CHURCH_NAME = "St Mary Church"
    }
}
