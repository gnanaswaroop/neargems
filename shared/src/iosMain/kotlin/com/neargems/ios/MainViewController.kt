package com.neargems.ios

import androidx.compose.ui.window.ComposeUIViewController
import coil3.SingletonImageLoader
import com.neargems.app.DriveContainer
import com.neargems.app.PlatformServices
import com.neargems.app.neargemsImageLoader
import com.neargems.data.neargemsHttpClient
import com.neargems.ui.NearGemsApp
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import kotlinx.coroutines.flow.flowOf
import platform.UIKit.UIViewController

private val iosHttp: HttpClient by lazy { neargemsHttpClient(Darwin) }

private val iosContainer: DriveContainer by lazy {
    DriveContainer(
        PlatformServices(
            http = iosHttp,
            keyValues = IosKeyValueStore(),
            location = IosLocationSource(),
            headings = IosHeadingSource(),
            narrator = IosNarrator(),
            maps = IosMapOpener(),
            // CarPlay is not connected yet; the car screen is a later step.
            carConnected = flowOf(false),
            language = deviceLanguage(),
            clock = ::nowMillis,
        )
    )
}

/** The Compose screen for Swift to host. Called once from the SwiftUI app. */
fun MainViewController(): UIViewController {
    SingletonImageLoader.setSafe { context -> neargemsImageLoader(context, iosHttp) }
    // The screen is on show for as long as this controller exists; live location and the compass follow it.
    iosContainer.setVisible(true)
    return ComposeUIViewController {
        NearGemsApp(
            container = iosContainer,
            onStart = { iosContainer.refresh() },
            onWatchChanged = { },
        )
    }
}
