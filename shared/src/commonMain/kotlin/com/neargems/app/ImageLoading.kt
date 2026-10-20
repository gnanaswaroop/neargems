package com.neargems.app

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.annotation.ExperimentalCoilApi
import coil3.util.DebugLogger
import coil3.network.ktor3.KtorNetworkFetcherFactory
import io.ktor.client.HttpClient

/**
 * Image loader for Wikipedia photos. They are fetched over the network at runtime and never bundled,
 * and they go through the same [HttpClient] as the rest of the app, so the User-Agent and timeouts match.
 */
@OptIn(ExperimentalCoilApi::class)
fun neargemsImageLoader(context: PlatformContext, http: HttpClient): ImageLoader =
    ImageLoader.Builder(context)
        .components { add(KtorNetworkFetcherFactory(http)) }
        // Temporary: logs every image request so the map tile placement can be checked.
        .logger(DebugLogger())
        .build()
