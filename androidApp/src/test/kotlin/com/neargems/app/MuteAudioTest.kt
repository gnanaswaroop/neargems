package com.neargems.app

import com.neargems.platform.KeyValueStore
import com.neargems.platform.Narrator
import com.neargems.support.Fixtures
import com.neargems.support.FixedLocation
import com.neargems.support.MemoryStore
import com.neargems.support.NoHeadings
import com.neargems.support.NoMaps
import com.neargems.support.SilentNarrator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The mute switch silences every spoken announcement, including while a car is connected. The
 * container is built with a narrator that records what it is asked to say.
 */
class MuteAudioTest {

    private val spoken = mutableListOf<String>()
    private var stops = 0

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun aSpokenPlaceIsHeardUnlessAudioIsMuted() {
        val container = container(keyValues = MemoryStore(), carConnected = true)
        container.refresh()
        awaitUntil { container.state.value.current != null }
        // Connecting to a car announces the top pick too. Wait for that, then check only the explicit read-aloud.
        awaitUntil { spoken.isNotEmpty() }
        spoken.clear()

        container.speakCurrent()
        assertEquals(1, spoken.size)

        container.updateSettings { it.copy(muteAudio = true) }
        spoken.clear()
        container.speakCurrent()
        assertTrue("nothing should be spoken while muted", spoken.isEmpty())
    }

    @Test
    fun muteStopsSpeechThatIsAlreadyPlaying() {
        val container = container(keyValues = MemoryStore(), carConnected = true)
        container.updateSettings { it.copy(muteAudio = true) }
        awaitUntil { stops > 0 }
        assertTrue("turning mute on should stop current speech", stops > 0)
    }

    @Test
    fun theMuteChoiceIsRememberedAfterARestart() {
        val keyValues = MemoryStore()
        container(keyValues = keyValues, carConnected = false).updateSettings { it.copy(muteAudio = true) }
        awaitUntil { keyValues.getString("mute_audio") == "true" }

        val restarted = container(keyValues = keyValues, carConnected = false)
        assertTrue(restarted.settings.settings.value.muteAudio)
    }

    private fun container(keyValues: KeyValueStore, carConnected: Boolean): DriveContainer =
        DriveContainer(
            PlatformServices(
                http = Fixtures.httpClient(),
                keyValues = keyValues,
                location = FixedLocation(Fixtures.CAFE, emptyFlow()),
                headings = NoHeadings(),
                narrator = RecordingNarrator(),
                maps = NoMaps(),
                carConnected = flowOf(carConnected),
                language = "en",
                clock = { 0L },
            ),
        )

    private inner class RecordingNarrator : Narrator {
        override fun speak(text: String) {
            spoken += text
        }

        override fun stop() {
            stops++
        }
    }

    private fun awaitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (!condition() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
