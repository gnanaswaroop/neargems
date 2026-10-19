package com.nearwise.android

import android.app.Application
import com.nearwise.android.car.CarConnectionMonitor
import com.nearwise.android.platform.AndroidHeadingSource
import com.nearwise.android.platform.AndroidLocationSource
import com.nearwise.android.platform.AndroidMapOpener
import com.nearwise.android.platform.AndroidNarrator
import com.nearwise.android.platform.SharedPrefsStore
import com.nearwise.app.DriveContainer
import com.nearwise.app.PlatformServices
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.util.DebugLogger
import io.ktor.client.plugins.HttpTimeout
import java.util.Locale
import okhttp3.OkHttpClient

private const val USER_AGENT = "Nearwise/0.1 (personal project)"

class NearwiseApplication : Application(), SingletonImageLoader.Factory {
    lateinit var container: DriveContainer
        private set

    /**
     * Wikimedia refuses image requests that do not identify the app (it answers 403), so the image
     * client sends the same User-Agent as the Wikipedia API calls.
     */
    private val imageClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
            }
            .build()
    }

    /** Wikipedia photos are fetched over the network at runtime; nothing is bundled. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { imageClient })) }
            .logger(DebugLogger())
            .build()

    override fun onCreate() {
        super.onCreate()
        container = DriveContainer(
            PlatformServices(
                http = HttpClient(OkHttp) {
                    install(HttpTimeout) {
                        connectTimeoutMillis = 10_000
                        requestTimeoutMillis = 20_000
                    }
                },
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
