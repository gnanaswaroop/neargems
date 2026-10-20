package com.neargem.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WikipediaSourceTest {

    @Test
    fun citiesAndRegionsAreTreatedAsAreas() {
        assertTrue(WikipediaSource.isAreaArticle("London is the capital and largest city of England and the United Kingdom."))
        assertTrue(WikipediaSource.isAreaArticle("Kent is a county in South East England."))
        assertTrue(WikipediaSource.isAreaArticle("Camden is a London borough."))
    }

    @Test
    fun placesAreKept() {
        assertFalse(WikipediaSource.isAreaArticle("Tower Bridge is a bascule bridge over the River Thames in London."))
        assertFalse(WikipediaSource.isAreaArticle("The Eiffel Tower is a wrought-iron lattice tower on the Champ de Mars."))
        assertFalse(WikipediaSource.isAreaArticle("Hyde Park is a major public park in central London."))
    }

    @Test
    fun trimSnippetCollapsesWhitespaceAndCutsAtAWord() {
        assertEquals("a b", WikipediaSource.trimSnippet("a   b"))
        val long = "word ".repeat(100)
        val trimmed = WikipediaSource.trimSnippet(long)
        assertTrue(trimmed.endsWith("…"))
        assertTrue(trimmed.length <= 241)
    }
}
