package com.neargem.android

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.neargem.android.car.CarConnectionMonitor
import com.neargem.android.platform.AndroidHeadingSource
import com.neargem.android.platform.AndroidLocationSource
import com.neargem.android.platform.AndroidMapOpener
import com.neargem.android.platform.AndroidNarrator
import com.neargem.android.platform.SharedPrefsStore
import com.neargem.app.DriveContainer
import com.neargem.app.PlatformServices
import com.neargem.app.neargemImageLoader
import com.neargem.data.neargemHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.util.Locale

class NearGemApplication : Application(), SingletonImageLoader.Factory {
    lateinit var container: DriveContainer
        private set

    private val http: HttpClient by lazy { neargemHttpClient(OkHttp) }

    override fun newImageLoader(context: PlatformContext): ImageLoader = neargemImageLoader(context, http)

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
            )
        )
    }
}
