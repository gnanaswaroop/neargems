package com.drivescout.android

import android.app.Application
import com.drivescout.android.car.CarConnectionMonitor
import com.drivescout.android.platform.AndroidLocationSource
import com.drivescout.android.platform.AndroidMapOpener
import com.drivescout.android.platform.AndroidNarrator
import com.drivescout.android.platform.SharedPrefsStore
import com.drivescout.app.DriveContainer
import com.drivescout.app.PlatformServices
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.util.Locale

class DriveScoutApplication : Application() {
    lateinit var container: DriveContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DriveContainer(
            PlatformServices(
                http = HttpClient(OkHttp),
                keyValues = SharedPrefsStore(this),
                location = AndroidLocationSource(this),
                narrator = AndroidNarrator(this),
                maps = AndroidMapOpener(this),
                carConnected = CarConnectionMonitor(this).projecting,
                language = Locale.getDefault().language.ifBlank { "en" },
                clock = System::currentTimeMillis,
            )
        )
    }
}
