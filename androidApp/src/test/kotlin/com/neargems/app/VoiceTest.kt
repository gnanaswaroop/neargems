package com.neargems.app

import com.neargems.platform.KeyValueStore
import com.neargems.platform.Narrator
import com.neargems.platform.SpeechVoice
import com.neargems.support.Fixtures
import com.neargems.support.FixedLocation
import com.neargems.support.MemoryStore
import com.neargems.support.NoHeadings
import com.neargems.support.NoMaps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The Voice setting: the choice is listed, remembered, applied to the narrator and heard as a sample. */
class VoiceTest {

    private val spoken = mutableListOf<String>()
    private val applied = mutableListOf<String?>()
    private val installed = MutableStateFlow(listOf(SpeechVoice("en-gb-a", "English (UK), high quality")))

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun theVoicesTheNarratorHasInstalledAreShownInSettings() {
        val container = container(MemoryStore())
        assertEquals(installed.value, container.voices.value)
    }

    @Test
    fun choosingAVoiceAppliesItAndSpeaksASample() {
        val container = container(MemoryStore())
        container.chooseVoice("en-gb-a")

        assertEquals("en-gb-a", container.settings.settings.value.voice)
        assertEquals("en-gb-a", applied.last())
        // With a place on screen the voice reads that place; with none it reads the sample.
        assertEquals(1, spoken.size)
        assertTrue(spoken.single() == DriveContainer.VOICE_SAMPLE || spoken.single().contains("Corner Cafe"))
    }

    @Test
    fun theChosenVoiceIsRememberedAndAppliedAfterARestart() {
        val keyValues = MemoryStore()
        container(keyValues).chooseVoice("en-gb-a")

        applied.clear()
        val restarted = container(keyValues)
        assertEquals("en-gb-a", restarted.settings.settings.value.voice)
        assertEquals(listOf<String?>("en-gb-a"), applied)
    }

    @Test
    fun choosingTheDeviceDefaultClearsTheSavedVoice() {
        val keyValues = MemoryStore()
        val container = container(keyValues)
        container.chooseVoice("en-gb-a")
        container.chooseVoice(null)

        assertNull(container.settings.settings.value.voice)
        assertNull(container(keyValues).settings.settings.value.voice)
    }

    private fun container(keyValues: KeyValueStore): DriveContainer =
        DriveContainer(
            PlatformServices(
                http = Fixtures.httpClient(),
                keyValues = keyValues,
                location = FixedLocation(Fixtures.CAFE, emptyFlow()),
                headings = NoHeadings(),
                narrator = VoiceNarrator(),
                maps = NoMaps(),
                carConnected = flowOf(false),
                language = "en",
                clock = { 0L },
            ),
        )

    private inner class VoiceNarrator : Narrator {
        override val voices: StateFlow<List<SpeechVoice>> = installed

        override fun speak(text: String) {
            spoken += text
        }

        override fun stop() = Unit

        override fun useVoice(id: String?) {
            applied += id
        }
    }
}
