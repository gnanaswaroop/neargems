package com.neargems.data

import com.neargems.domain.LatLon
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.net.URLDecoder

/** The keyword part of the named-places query: what text reaches Overpass for a given list of keywords. */
class OverpassEscapeTest {

    /** The regex inside the query's name filter, for the keywords sent. */
    private fun patternFor(keywords: List<String>): String = runBlocking {
        var sent = ""
        val client = HttpClient(
            MockEngine { request ->
                val form = (request.body as TextContent).text
                sent = URLDecoder.decode(form.removePrefix("data="), "UTF-8")
                respond(
                    content = """{"elements":[]}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        )
        OverpassSource(Http(client)).fetchNamed(LatLon(51.5, -0.12), 500, keywords)
        Regex("""nwr\["name"~"\((.*)\)",i\]""").find(sent)?.groupValues?.get(1)
            ?: error("no name filter in query: $sent")
    }

    @Test
    fun aPlainKeywordIsSentUnchanged() {
        assertEquals("church|temple", patternFor(listOf("church", "temple")))
    }

    @Test
    fun aDotIsEscapedSoItMatchesOnlyADot() {
        // Overpass reads one backslash in a quoted string as an escape, so a regex escape needs two.
        assertEquals("""st\\. james""", patternFor(listOf("st. james")))
    }

    @Test
    fun parenthesesAreEscapedSoTheyStayLiteral() {
        assertEquals("""\\(x\\)""", patternFor(listOf("(x)")))
    }

    @Test
    fun quotesCannotEndTheQueryString() {
        val pattern = patternFor(listOf("st. \"james\""))
        // Every quote in the pattern is escaped, so none can end the quoted string in the query.
        assertFalse("an unescaped quote would end the query string: $pattern", Regex("""(?<!\\)"""").containsMatchIn(pattern))
    }

    @Test
    fun backslashesCannotChangeTheMeaningOfTheRegex() {
        // A backslash is dropped rather than escaped: an escaped one turns into a regex escape such as \b.
        assertEquals("ab", patternFor(listOf("""a\b""")))
    }
}
