package com.neargems.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The card shows whole sentences that fit, so nothing is cut off half-way through a sentence. */
class SummaryTest {

    private val text = "The South Bank Lion is an 1837 sculpture in Central London. " +
        "Since 1966 it has stood next to County Hall, on the South Bank of the River Thames."

    @Test
    fun keepsWholeSentencesThatFit() {
        assertEquals(
            "The South Bank Lion is an 1837 sculpture in Central London.",
            summarise(text, maxChars = 100),
        )
    }

    @Test
    fun showsTheWholeTextWhenItFits() {
        assertEquals(text, summarise(text, maxChars = 400))
    }

    @Test
    fun cutsAOverlongFirstSentenceAtAWordWithAnEllipsis() {
        val result = summarise(text, maxChars = 30)
        assertTrue(result.endsWith("…"))
        assertTrue(result.length <= 30)
        assertTrue(text.startsWith(result.dropLast(1)))
    }

    @Test
    fun emptyTextStaysEmpty() {
        assertEquals("", summarise("", maxChars = 50))
    }
}
