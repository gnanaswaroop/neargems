@file:OptIn(ExperimentalForeignApi::class)

package com.nearwise.ios

import com.nearwise.domain.LatLon
import com.nearwise.domain.Poi
import com.nearwise.platform.HeadingSource
import com.nearwise.platform.KeyValueStore
import com.nearwise.platform.LocationSource
import com.nearwise.platform.MapOpener
import com.nearwise.platform.Narrator
import io.ktor.http.encodeURLParameter
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionDuckOthers
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVSpeechBoundary
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechUtterance
import platform.CoreLocation.CLHeading
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.Foundation.NSDate
import platform.Foundation.NSLocale
import platform.Foundation.NSURL
import platform.Foundation.NSUserDefaults
import platform.Foundation.currentLocale
import platform.Foundation.languageCode
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIApplication
import platform.darwin.NSObject

/** Key-value storage on NSUserDefaults. */
class IosKeyValueStore : KeyValueStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun getString(key: String): String? = defaults.stringForKey(key)

    override fun putString(key: String, value: String?) {
        if (value == null) defaults.removeObjectForKey(key) else defaults.setObject(value, key)
    }
}

/** A position stream from CoreLocation. Asks for when-in-use permission the first time it is collected. */
class IosLocationSource : LocationSource {

    override suspend fun current(): LatLon? = suspendCancellableCoroutine { continuation ->
        val manager = CLLocationManager()
        val delegate = LocationDelegate(onLocation = { location ->
            manager.stopUpdatingLocation()
            if (continuation.isActive) continuation.resumeWith(Result.success(location.toLatLon()))
        })
        manager.delegate = delegate
        manager.requestWhenInUseAuthorization()
        manager.requestLocation()
        continuation.invokeOnCancellation { manager.stopUpdatingLocation() }
    }

    override fun updates(): Flow<LatLon> = callbackFlow {
        val manager = CLLocationManager()
        manager.delegate = LocationDelegate(onLocation = { location -> trySend(location.toLatLon()) })
        manager.desiredAccuracy = kCLLocationAccuracyBest
        manager.distanceFilter = 5.0
        manager.requestWhenInUseAuthorization()
        manager.startUpdatingLocation()
        awaitClose { manager.stopUpdatingLocation() }
    }
}

/** Compass heading from CoreLocation: true north when it is known, otherwise magnetic north. */
class IosHeadingSource : HeadingSource {

    override fun headings(): Flow<Float> = callbackFlow {
        val manager = CLLocationManager()
        manager.delegate = LocationDelegate(onHeading = { heading ->
            val degrees = if (heading.trueHeading >= 0) heading.trueHeading else heading.magneticHeading
            trySend(degrees.toFloat())
        })
        manager.startUpdatingHeading()
        awaitClose { manager.stopUpdatingHeading() }
    }
}

/** Speech on AVSpeechSynthesizer. Music is ducked while a suggestion is spoken. */
class IosNarrator : Narrator {
    private val synthesizer = AVSpeechSynthesizer()

    init {
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayback, AVAudioSessionCategoryOptionDuckOthers, null)
    }

    override fun speak(text: String) {
        synthesizer.speakUtterance(AVSpeechUtterance(string = text))
    }

    override fun stop() {
        synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
    }
}

/** Opens the Apple Maps app at a place, or a web link in the browser. */
class IosMapOpener : MapOpener {
    override fun open(poi: Poi) {
        val name = poi.name.encodeURLParameter()
        openUrl("https://maps.apple.com/?ll=${poi.location.lat},${poi.location.lon}&q=$name")
    }

    override fun openUrl(url: String) {
        NSURL.URLWithString(url)?.let { target ->
            UIApplication.sharedApplication.openURL(target, options = emptyMap<Any?, Any?>(), completionHandler = null)
        }
    }
}

/** Receives CoreLocation callbacks and hands them to plain Kotlin lambdas. */
private class LocationDelegate(
    private val onLocation: ((CLLocation) -> Unit)? = null,
    private val onHeading: ((CLHeading) -> Unit)? = null,
) : NSObject(), CLLocationManagerDelegateProtocol {

    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        val latest = didUpdateLocations.lastOrNull() as? CLLocation ?: return
        onLocation?.invoke(latest)
    }

    override fun locationManager(manager: CLLocationManager, didUpdateHeading: CLHeading) {
        onHeading?.invoke(didUpdateHeading)
    }
}

private fun CLLocation.toLatLon(): LatLon =
    coordinate.useContents { LatLon(latitude, longitude) }

/** Wall-clock time in milliseconds, for the dismissal expiry. */
fun nowMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

/** The device language, to pick the Wikipedia edition. */
fun deviceLanguage(): String = NSLocale.currentLocale.languageCode.ifBlank { "en" }
