package com.drivescout.android.platform

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.drivescout.domain.LatLon
import com.drivescout.domain.Poi
import com.drivescout.platform.KeyValueStore
import com.drivescout.platform.LocationSource
import com.drivescout.platform.MapOpener
import com.drivescout.platform.Narrator
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

class SharedPrefsStore(context: Context) : KeyValueStore {
    private val prefs = context.getSharedPreferences("drivescout", Context.MODE_PRIVATE)

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(key: String, value: String?) {
        prefs.edit().putString(key, value).apply()
    }
}

class AndroidLocationSource(context: Context) : LocationSource {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    /** A fresh fix if one arrives quickly, otherwise the last known position. */
    @SuppressLint("MissingPermission")
    override suspend fun current(): LatLon? {
        val location = try {
            withTimeoutOrNull(FIX_TIMEOUT_MS) {
                client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
            } ?: client.lastLocation.await()
        } catch (e: SecurityException) {
            null
        }
        return location?.let { LatLon(it.latitude, it.longitude) }
    }

    private companion object {
        const val FIX_TIMEOUT_MS = 8_000L
    }
}

/** Speaks on the navigation audio channel and ducks music, the same way turn prompts do. */
class AndroidNarrator(context: Context) : Narrator {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private var focusRequest: AudioFocusRequest? = null
    private var ready = false
    private var pending: String? = null

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status -> onInit(status) }

    private fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        tts.language = Locale.getDefault()
        tts.setAudioAttributes(attributes)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = abandonFocus()
            override fun onError(utteranceId: String?) = abandonFocus()
        })
        ready = true
        pending?.let { speakNow(it) }
        pending = null
    }

    override fun speak(text: String) {
        if (!ready) {
            pending = text
            return
        }
        speakNow(text)
    }

    override fun stop() {
        pending = null
        tts.stop()
        abandonFocus()
    }

    private fun speakNow(text: String) {
        requestFocus()
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    private fun requestFocus() {
        if (focusRequest != null) return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .build()
        audioManager.requestAudioFocus(request)
        focusRequest = request
    }

    private fun abandonFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private companion object {
        const val UTTERANCE_ID = "drivescout-suggestion"
    }
}

class AndroidMapOpener(private val context: Context) : MapOpener {
    override fun open(poi: Poi) {
        val lat = poi.location.lat
        val lon = poi.location.lon
        val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode(poi.name)})")
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
