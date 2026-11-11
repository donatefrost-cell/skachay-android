package ru.skachay.app.choice

import android.content.Context
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import java.io.IOException
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import okhttp3.Dns
import okhttp3.OkHttpClient

internal fun createThumbnailImageLoader(context: Context): ImageLoader {
    val client = thumbnailHttpClient()
    return ImageLoader.Builder(context.applicationContext)
        .components {
            add(OkHttpNetworkFetcherFactory(callFactory = { client }))
        }
        .crossfade(true)
        .build()
}

private fun thumbnailHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .callTimeout(15, TimeUnit.SECONDS)
    .connectTimeout(8, TimeUnit.SECONDS)
    .readTimeout(10, TimeUnit.SECONDS)
    .writeTimeout(10, TimeUnit.SECONDS)
    .followRedirects(false)
    .followSslRedirects(false)
    .dns(Dns { hostname ->
        val addresses = Dns.SYSTEM.lookup(hostname)
        if (addresses.isEmpty() || addresses.any(::isNonPublicAddress)) {
            throw UnknownHostException("Thumbnail host did not resolve to a public address")
        }
        addresses
    })
    .addInterceptor { chain ->
        var request = chain.request()
        repeat(MAX_THUMBNAIL_REDIRECTS + 1) { hop ->
            val url = request.url
            if (!url.isHttps || url.username.isNotEmpty() || url.password.isNotEmpty() || url.port != 443) {
                throw IOException("Unsafe thumbnail URL")
            }
            val response = chain.proceed(request)
            val location = response.header("Location")
            if (!response.isRedirect || location == null) return@addInterceptor response
            if (hop == MAX_THUMBNAIL_REDIRECTS) {
                response.close()
                throw IOException("Too many thumbnail redirects")
            }
            val redirected = url.resolve(location)
            response.close()
            if (redirected == null || !redirected.isHttps || redirected.username.isNotEmpty() ||
                redirected.password.isNotEmpty() || redirected.port != 443) {
                throw IOException("Unsafe thumbnail redirect")
            }
            request = request.newBuilder().url(redirected).build()
        }
        throw IOException("Too many thumbnail redirects")
    }
    .build()

private fun isNonPublicAddress(address: InetAddress): Boolean {
    if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
        address.isSiteLocalAddress || address.isMulticastAddress) return true
    val bytes = (address as? Inet6Address)?.address ?: return false
    return bytes.isNotEmpty() && (bytes[0].toInt() and 0xfe) == 0xfc
}

private const val MAX_THUMBNAIL_REDIRECTS = 5
