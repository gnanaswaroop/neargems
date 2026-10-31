package com.neargems.app

import com.neargems.platform.Narrator
import com.neargems.platform.SpeechVoice
import kotlinx.coroutines.flow.StateFlow

/**
 * Passes speech to the platform narrator unless [muted] says audio is off. Used for every spoken
 * announcement, so the mute setting silences them all, including when a car is connected.
 */
class MutingNarrator(private val inner: Narrator, private val muted: () -> Boolean) : Narrator {
    override fun speak(text: String) {
        if (!muted()) inner.speak(text)
    }

    override fun stop() = inner.stop()

    override val voices: StateFlow<List<SpeechVoice>> get() = inner.voices

    override fun useVoice(id: String?) = inner.useVoice(id)
}
