package com.neargems.android.platform

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.neargems.domain.LatLon
import com.neargems.domain.Poi
import com.neargems.platform.KeyValueStore
import com.neargems.platform.LocationSource
import com.neargems.platform.MapOpener
import com.neargems.platform.Narrator
import com.neargems.platform.SpeechVoice
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

class SharedPrefsStore(context: Context) : KeyValueStore {
    private val prefs = context.getSharedPreferences("neargems", Context.MODE_PRIVATE)

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(key: String, value: String?) {
        prefs.edit().putString(key, value).apply()
    }
}

class AndroidLocationSource(context: Context) : LocationSource {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    /**
     * A recent last known position at once, so the search starts without waiting for GPS. Otherwise a fresh
     * fix if one arrives quickly, falling back to the last known position. Live updates correct the position
     * as soon as the phone reports it.
     */
    @SuppressLint("MissingPermission")
    override suspend fun current(): LatLon? {
        val location = try {
            val last = client.lastLocation.await()
            if (last != null && System.currentTimeMillis() - last.time < RECENT_FIX_MS) {
                last
            } else {
                withTimeoutOrNull(FIX_TIMEOUT_MS) {
                    client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
                } ?: last
            }
        } catch (e: SecurityException) {
            null
        }
        return location?.let { LatLon(it.latitude, it.longitude) }
    }

    /** Fused updates every few seconds or every few metres, whichever comes first. Stops when collection stops. */
    @SuppressLint("MissingPermission")
    override fun updates(): Flow<LatLon> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, UPDATE_INTERVAL_MS)
            .setMinUpdateDistanceMeters(MIN_UPDATE_METERS)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(LatLon(it.latitude, it.longitude)) }
            }
        }
        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            close()
        }
        awaitClose { client.removeLocationUpdates(callback) }
    }

    private companion object {
        const val FIX_TIMEOUT_MS = 8_000L
        /** A last known position newer than this is used without asking for a fresh fix. */
        const val RECENT_FIX_MS = 120_000L
        const val UPDATE_INTERVAL_MS = 4_000L
        const val MIN_UPDATE_METERS = 5f
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
    /** The voice the user chose, by id. Null uses the device default. */
    private var chosenVoice: String? = null
    private val voiceList = MutableStateFlow<List<SpeechVoice>>(emptyList())

    override val voices: StateFlow<List<SpeechVoice>> = voiceList.asStateFlow()

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status -> onInit(status) }

    private fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        tts.language = Locale.getDefault()
        tts.setAudioAttributes(attributes)
        voiceList.value = speakableVoices()
        applyVoice()
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

    override fun useVoice(id: String?) {
        chosenVoice = id
        if (ready) applyVoice()
    }

    /** Voices in the user's language, best quality first, offline before online. */
    private fun speakableVoices(): List<SpeechVoice> {
        val language = Locale.getDefault().language
        val installed = tts.voices.orEmpty()
            .filter { it.locale.language == language }
            .sortedWith(compareBy<android.speech.tts.Voice> { it.isNetworkConnectionRequired }.thenByDescending { it.quality })
            // The best voice of each accent (US, UK, India, Australia...) comes first, so a short list still has them all.
            .let { sorted ->
                val best = sorted.groupBy { it.locale.country }.values.map { it.first() }
                best + sorted.filter { it !in best }
            }
        val labels = installed.map { voice ->
            val quality = when {
                voice.quality >= QUALITY_VERY_HIGH -> "very high quality"
                voice.quality >= QUALITY_HIGH -> "high quality"
                else -> "standard quality"
            }
            val online = if (voice.isNetworkConnectionRequired) ", online" else ""
            "${voice.locale.displayName}, $quality$online"
        }
        val counts = labels.groupingBy { it }.eachCount()
        val seen = mutableMapOf<String, Int>()
        return installed.mapIndexed { index, voice ->
            val label = labels[index]
            val number = if ((counts[label] ?: 0) > 1) " ${seen.merge(label, 1, Int::plus)}" else ""
            SpeechVoice(id = voice.name, label = label + number)
        }
    }

    private fun applyVoice() {
        val voice = tts.voices.orEmpty().firstOrNull { it.name == chosenVoice } ?: tts.defaultVoice
        if (voice != null) tts.voice = voice
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
        const val UTTERANCE_ID = "neargems-suggestion"
        const val QUALITY_HIGH = 400
        const val QUALITY_VERY_HIGH = 500
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

    override fun openUrl(url: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
