package com.neargems.android

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.neargems.android.car.CarConnectionMonitor
import com.neargems.android.platform.AndroidHeadingSource
import com.neargems.android.platform.AndroidLocationSource
import com.neargems.android.platform.AndroidMapOpener
import com.neargems.android.platform.AndroidNarrator
import com.neargems.android.platform.AndroidPlaceNotifier
import com.neargems.android.platform.AndroidPostureSource
import com.neargems.android.platform.AndroidSeenStore
import com.neargems.android.platform.SharedPrefsStore
import com.neargems.app.DriveContainer
import com.neargems.app.PlatformServices
import com.neargems.app.neargemsImageLoader
import com.neargems.data.neargemsHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.util.Locale

class NearGemsApplication : Application(), SingletonImageLoader.Factory {
    lateinit var container: DriveContainer
        private set

    private val http: HttpClient by lazy { neargemsHttpClient(OkHttp) }

    override fun newImageLoader(context: PlatformContext): ImageLoader = neargemsImageLoader(context, http)

    override fun onCreate() {
        super.onCreate()
        container = DriveContainer(
            PlatformServices(
                http = http,
                keyValues = SharedPrefsStore(this),
                location = AndroidLocationSource(this),
                headings = AndroidHeadingSource(this),
                narrator = AndroidNarrator(this),
                maps = AndroidMapOpener(this),
                carConnected = CarConnectionMonitor(this).projecting,
                language = Locale.getDefault().language.ifBlank { "en" },
                clock = System::currentTimeMillis,
                seen = AndroidSeenStore(this),
                posture = AndroidPostureSource(this),
                notifier = AndroidPlaceNotifier(this),
            )
        )
    }
}
