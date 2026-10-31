package com.neargems.domain

import com.neargems.data.SettingsStore
import com.neargems.support.MemoryStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeywordSuggestionsTest {

    @Test
    fun wordsThatStartWithWhatIsTypedComeFirst() {
        val ideas = suggestKeywords("ch", chosen = emptyList())
        assertEquals("church", ideas.first())
        assertTrue(ideas.contains("ice cream") || ideas.all { it.contains("ch") })
    }

    @Test
    fun chosenWordsAreNotSuggestedAgain() {
        assertFalse(suggestKeywords("temp", chosen = listOf("temple")).contains("temple"))
    }

    @Test
    fun withNothingTypedTheIdeasAreOfferedInOrder() {
        assertEquals(SUGGESTED_KEYWORDS.take(8), suggestKeywords("", chosen = emptyList()))
    }

    @Test
    fun theListIsKeptShort() {
        assertTrue(suggestKeywords("a", chosen = emptyList()).size <= 8)
    }

    @Test
    fun savedKeywordsComeBackExactlyAfterARestart() {
        val store = MemoryStore()
        SettingsStore(store) { 0L }.update { it.copy(keywords = "temple, church, waterfall") }

        val restored = SettingsStore(store) { 0L }.settings.value.keywords
        assertEquals(listOf("temple", "church", "waterfall"), parseKeywords(restored))
    }

    @Test
    fun aKeywordWithSpacesSurvivesASave() {
        val store = MemoryStore()
        SettingsStore(store) { 0L }.update { it.copy(keywords = "dim sum, ice cream") }

        val restored = SettingsStore(store) { 0L }.settings.value.keywords
        assertEquals(listOf("dim sum", "ice cream"), parseKeywords(restored))
    }
}
