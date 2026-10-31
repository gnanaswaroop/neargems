package com.neargems.android

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.neargems.app.DriveContainer
import com.neargems.app.PlatformServices
import com.neargems.platform.HeadingSource
import com.neargems.platform.Narrator
import com.neargems.platform.SpeechVoice
import com.neargems.support.Fixtures
import com.neargems.support.FixedLocation
import com.neargems.support.MemoryStore
import com.neargems.support.NoMaps
import com.neargems.ui.NearGemsApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The newest behaviour on a device: the mute switch, Mark as visited, read-aloud and Next acting
 * on the card on screen, and the compass pointer. Runs the real screens with fake GPS, compass,
 * network and speech, which record what they are asked to say.
 */
@RunWith(AndroidJUnit4::class)
class NearGemsNewFeaturesTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val spoken = mutableListOf<String>()
    private val headings = MutableSharedFlow<Float>(replay = 1, extraBufferCapacity = 8)

    private fun container(): DriveContainer = DriveContainer(
        PlatformServices(
            http = Fixtures.httpClient(),
            keyValues = MemoryStore(),
            location = FixedLocation(Fixtures.CAFE, emptyFlow()),
            headings = ScriptedHeadings(headings),
            narrator = RecordingNarrator(spoken),
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
        rule.waitUntil(TIMEOUT_MS) {
            rule.onAllNodesWithText(Fixtures.CAFE_NAME).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun theMuteSwitchInSettingsSilencesAudio() {
        val container = container()
        showApp(container)

        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText(MUTE_LABEL).performScrollTo().performClick()
        rule.waitForIdle()

        assertTrue(container.settings.settings.value.muteAudio)
    }

    @Test
    fun theVoicePickerInSettingsChoosesAVoiceAndSpeaksASample() {
        val container = container()
        showApp(container)

        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText(VOICE_LABEL).performScrollTo().performClick()
        rule.waitForIdle()

        assertEquals(VOICE_ID, container.settings.settings.value.voice)
        assertTrue(spoken.contains(DriveContainer.VOICE_SAMPLE))
    }

    @Test
    fun theMuteButtonOnTheHomeScreenSilencesAudio() {
        val container = container()
        showApp(container)

        rule.onNodeWithContentDescription("Mute audio").performClick()
        rule.waitForIdle()

        assertTrue(container.settings.settings.value.muteAudio)
        rule.onNodeWithContentDescription("Audio muted. Tap to unmute").assertIsDisplayed()
    }

    @Test
    fun aKeywordTypedInSettingsNarrowsTheSuggestions() {
        val container = container()
        showApp(container)

        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Keywords, comma-separated").performScrollTo().performTextInput("luigi")
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Done").performClick()

        rule.waitUntil(TIMEOUT_MS) {
            rule.onAllNodesWithText(Fixtures.RESTAURANT_NAME).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(rule.onAllNodesWithText(Fixtures.CAFE_NAME).fetchSemanticsNodes().isEmpty())
        assertEquals("luigi", container.settings.settings.value.keywords)
    }

    @Test
    fun theSunReadoutShowsOnTheCardOnceThereIsAPosition() {
        val container = container()
        showApp(container)

        rule.waitUntil(TIMEOUT_MS) {
            rule.onAllNodesWithText("Sun ", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodesWithText("Sun ", substring = true)[0].assertIsDisplayed()
    }

    @Test
    fun aVisitedPlaceCanBeShownAgainFromTheHistory() {
        val container = container()
        showApp(container)

        rule.onAllNodesWithText("Mark as visited")[0].performClick()
        rule.waitUntil(TIMEOUT_MS) { container.visited.value.isNotEmpty() }

        rule.onNodeWithContentDescription("Visited places").performClick()
        rule.onNodeWithText(container.visited.value.first().name).assertIsDisplayed()
        rule.onNodeWithText("Show again").performClick()
        rule.waitUntil(TIMEOUT_MS) { container.visited.value.isEmpty() }
        assertTrue(container.settings.visitedIds().isEmpty())
    }

    @Test
    fun markAsVisitedHidesThePlaceFromTheCard() {
        val container = container()
        showApp(container)

        rule.onAllNodesWithText("Mark as visited")[0].performClick()

        rule.waitUntil(TIMEOUT_MS) {
            rule.onAllNodesWithText(Fixtures.RESTAURANT_NAME).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(rule.onAllNodesWithText(Fixtures.CAFE_NAME).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun readAloudSpeaksTheCardThatIsOnScreen() {
        val container = container()
        showApp(container)

        // Swipe to the next card, then read aloud: the spoken text must be that card, not the top pick.
        rule.onRoot().performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.onNodeWithText("Read aloud").performClick()
        rule.waitForIdle()

        assertTrue("nothing was spoken", spoken.isNotEmpty())
        assertTrue("spoke: ${spoken.last()}", spoken.last().startsWith(Fixtures.RESTAURANT_NAME))
    }

    @Test
    fun theCompassPointerAppearsOnceTheHeadingIsKnown() {
        val container = container()
        showApp(container)
        headings.tryEmit(90f)

        rule.waitUntil(TIMEOUT_MS) { container.headingDegrees.value == 90f }
        rule.onAllNodesWithContentDescription("Direction pointer", substring = true)[0].assertIsDisplayed()
        assertEquals(90f, container.headingDegrees.value)
    }

    private class RecordingNarrator(private val spoken: MutableList<String>) : Narrator {
        override val voices: StateFlow<List<SpeechVoice>> =
            MutableStateFlow(listOf(SpeechVoice(VOICE_ID, VOICE_LABEL)))

        override fun speak(text: String) {
            spoken += text
        }

        override fun stop() = Unit
    }

    private class ScriptedHeadings(private val readings: Flow<Float>) : HeadingSource {
        override fun headings(): Flow<Float> = readings
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val MUTE_LABEL = "Mute audio, even when a car is connected"
        const val VOICE_ID = "en-gb-test"
        const val VOICE_LABEL = "English (UK), high quality"
    }
}
