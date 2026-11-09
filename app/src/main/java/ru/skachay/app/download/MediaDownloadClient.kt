package ru.skachay.app.download

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/** Shared connection pool for media byte transfers. OkHttp negotiates HTTP/2 when the host supports it. */
internal object MediaDownloadClient {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    val downloader: DirectDownloader = DirectDownloader(client)
}
