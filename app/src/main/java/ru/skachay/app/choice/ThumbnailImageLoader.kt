package ru.skachay.app.choice

import android.content.Context
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import ru.skachay.app.network.UrlNetworkPolicy

internal fun createThumbnailImageLoader(context: Context): ImageLoader {
    val client =
        UrlNetworkPolicy.publicHttpsClient(
            callTimeoutSeconds = 15,
            connectTimeoutSeconds = 8,
            readTimeoutSeconds = 10,
            writeTimeoutSeconds = 10,
        )
    return ImageLoader
        .Builder(context.applicationContext)
        .components {
            add(OkHttpNetworkFetcherFactory(callFactory = { client }))
        }.build()
}
