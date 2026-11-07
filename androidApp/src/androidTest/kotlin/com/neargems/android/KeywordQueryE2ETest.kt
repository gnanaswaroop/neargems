package com.neargems.android

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.neargems.app.DriveContainer
import com.neargems.app.PlatformServices
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URLDecoder

/**
 * A keyword with punctuation is escaped into the Overpass query, so the query stays valid and the place it names
 * shows. The fake server accepts only a query whose keyword is escaped the way the app sends it.
 */
@RunWith(AndroidJUnit4::class)
class KeywordQueryE2ETest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val keywordPlace = """{"elements":[{"type":"node","id":99,"lat":51.5045,"lon":-0.1200,
        "tags":{"name":"St. James Cafe","amenity":"cafe"}}]}"""

    @Test
    fun aKeywordWithAFullStopFindsItsPlace() {
        val sent = mutableListOf<String>()
        val http = HttpClient(
            MockEngine { request ->
                val query = URLDecoder.decode((request.body as TextContent).text.removePrefix("data="), "UTF-8")
                sent += query
                if (query.contains("\"name\"~")) {
                    val pattern = Regex("""nwr\["name"~"\((.*)\)",i\]""").find(query)?.groupValues?.get(1)
                    // "st. james" is sent as st\. james: the dot is escaped so it matches only a dot.
                    return@MockEngine if (pattern == "st" + "\\" + "\\" + ". james") {
                        respond(keywordPlace, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    } else {
                        respond("bad query: $query", HttpStatusCode.BadRequest)
                    }
                }
                respond(Fixtures.overpassBody, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        )
        val container = DriveContainer(
            PlatformServices(
                http = http,
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
        rule.waitUntil(TIMEOUT_MS) { rule.onAllNodesWithText(Fixtures.CAFE_NAME).fetchSemanticsNodes().isNotEmpty() }

        container.updateSettings { it.copy(keywords = "st. james") }

        rule.waitUntil(SEARCH_TIMEOUT_MS) {
            rule.onAllNodesWithText("St. James Cafe").fetchSemanticsNodes().isNotEmpty() ||
                container.state.value.warning != null
        }
        val named = sent.filter { it.contains("\"name\"~") }
        assertTrue(
            "keyword place not shown; warning=${container.state.value.warning}; named queries=$named",
            rule.onAllNodesWithText("St. James Cafe").fetchSemanticsNodes().isNotEmpty(),
        )
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val SEARCH_TIMEOUT_MS = 60_000L
    }
}
