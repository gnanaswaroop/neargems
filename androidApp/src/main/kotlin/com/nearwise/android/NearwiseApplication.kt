package com.nearwise.android

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.nearwise.android.car.CarConnectionMonitor
import com.nearwise.android.platform.AndroidHeadingSource
import com.nearwise.android.platform.AndroidLocationSource
import com.nearwise.android.platform.AndroidMapOpener
import com.nearwise.android.platform.AndroidNarrator
import com.nearwise.android.platform.SharedPrefsStore
import com.nearwise.app.DriveContainer
import com.nearwise.app.PlatformServices
import com.nearwise.app.nearwiseImageLoader
import com.nearwise.data.nearwiseHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.util.Locale

class NearwiseApplication : Application(), SingletonImageLoader.Factory {
    lateinit var container: DriveContainer
        private set

    private val http: HttpClient by lazy { nearwiseHttpClient(OkHttp) }

    override fun newImageLoader(context: PlatformContext): ImageLoader = nearwiseImageLoader(context, http)

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
