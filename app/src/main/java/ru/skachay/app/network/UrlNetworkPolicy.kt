package ru.skachay.app.network

import java.io.IOException
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.OkHttpClient

/** Redirect and DNS guard for media, update, and thumbnail requests. */
internal object UrlNetworkPolicy {
    const val MAX_REDIRECTS = 5

    fun resolveRedirect(source: HttpUrl, location: String, redirectsFollowed: Int, allowedRedirectHostSuffixes: Set<String>? = null): HttpUrl? {
        if (redirectsFollowed >= MAX_REDIRECTS || !isSafeHttpsUrl(source)) return null
        val target = source.resolve(location) ?: return null
        if (!isSafeHttpsUrl(target)) return null
        if (allowedRedirectHostSuffixes != null && allowedRedirectHostSuffixes.none { hostMatchesSuffix(target.host, it) }) return null
        return target
    }

    fun hostMatchesSuffix(host: String, suffix: String): Boolean =
        host.equals(suffix, ignoreCase = true) || host.endsWith(".$suffix", ignoreCase = true)

    fun isSafeHttpsUrl(url: HttpUrl): Boolean {
        if (!url.isHttps || url.port != 443 || url.username.isNotEmpty() || url.password.isNotEmpty()) return false
        val host = url.host
        if (host.isBlank()) return false
        val isIpLiteral = ':' in host || host.all { it.isDigit() || it == '.' }
        if (isIpLiteral) {
            val address = try { InetAddress.getByName(host) } catch (_: Exception) { return false }
            if (!isPublicAddress(address)) return false
        }
        return true
    }

    fun isPublicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress) return false
        val bytes = address.address.map { it.toInt() and 0xff }
        if (address is Inet6Address) {
            if (bytes.size != 16) return false
            if (bytes.take(10).all { it == 0 } && bytes[10] == 0xff && bytes[11] == 0xff) {
                return isPublicIpv4(bytes.drop(12))
            }
            val globalUnicast = (bytes[0] and 0xe0) == 0x20
            val documentation = bytes[0] == 0x20 && bytes[1] == 0x01 && bytes[2] == 0x0d && bytes[3] == 0xb8
            val documentationV2 = bytes[0] == 0x3f && bytes[1] == 0xff
            val specialUse2001 = bytes[0] == 0x20 && bytes[1] == 0x01 && bytes[2] <= 1
            val sixToFour = bytes[0] == 0x20 && bytes[1] == 0x02
            return globalUnicast && !documentation && !documentationV2 && !specialUse2001 && !sixToFour
        }
        return isPublicIpv4(bytes)
    }

    fun publicHttpsClient(
        callTimeoutSeconds: Long? = null,
        connectTimeoutSeconds: Long = 20,
        readTimeoutSeconds: Long = 45,
        writeTimeoutSeconds: Long = 45,
        allowedRedirectHostSuffixes: Set<String>? = null,
    ): OkHttpClient = OkHttpClient.Builder()
        .apply { callTimeoutSeconds?.let { callTimeout(it, TimeUnit.SECONDS) } }
        .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
        .writeTimeout(writeTimeoutSeconds, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(false)
        .followSslRedirects(false)
        .dns(Dns { hostname ->
            val addresses = Dns.SYSTEM.lookup(hostname)
            if (addresses.isEmpty() || addresses.any { !isPublicAddress(it) }) {
                throw UnknownHostException("Host resolved to a non-public address")
            }
            addresses
        })
        .addInterceptor { chain ->
            var request = chain.request()
            repeat(MAX_REDIRECTS + 1) { hop ->
                if (!isSafeHttpsUrl(request.url)) throw IOException("Unsafe HTTPS request URL")
                val response = chain.proceed(request)
                val location = response.header("Location")
                if (!response.isRedirect || location == null) return@addInterceptor response
                val target = resolveRedirect(request.url, location, hop, allowedRedirectHostSuffixes)
                if (target == null) {
                    response.close()
                    throw IOException("Unsafe or excessive HTTPS redirect")
                }
                response.close()
                val redirected = request.newBuilder().url(target)
                if (!request.url.host.equals(target.host, ignoreCase = true)) {
                    redirected.removeHeader("Authorization")
                        .removeHeader("Cookie")
                        .removeHeader("Proxy-Authorization")
                        .removeHeader("Referer")
                        .removeHeader("Origin")
                        .removeHeader("Host")
                }
                request = redirected.build()
            }
            throw IOException("Too many HTTPS redirects")
        }
        .build()

    private fun isPublicIpv4(bytes: List<Int>): Boolean {
        if (bytes.size != 4) return false
        val a = bytes[0]; val b = bytes[1]; val c = bytes[2]
        return when {
            a == 0 || a == 10 || a == 127 || a >= 224 -> false
            a == 100 && b in 64..127 -> false
            a == 169 && b == 254 -> false
            a == 172 && b in 16..31 -> false
            a == 192 && b == 0 -> false
            a == 192 && b == 88 && c == 99 -> false
            a == 192 && b == 168 -> false
            a == 198 && b in setOf(18, 19) -> false
            a == 198 && b == 51 && c == 100 -> false
            a == 203 && b == 0 && c == 113 -> false
            else -> true
        }
    }
}
