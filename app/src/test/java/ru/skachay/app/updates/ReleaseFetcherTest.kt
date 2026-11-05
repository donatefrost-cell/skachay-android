package ru.skachay.app.updates

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseFetcherTest {
    @Test
    fun readsAvailableReleaseAndReportsAnUnavailableEndpoint() {
        val json = """{"tag_name":"v1.0","draft":false,"prerelease":false,"assets":[{"name":"app-release.apk","browser_download_url":"https://github.com/donatefrost-cell/skachay-android/releases/download/v1.0/app-release.apk"}]}""".toByteArray()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/latest") { exchange ->
            exchange.sendResponseHeaders(200, json.size.toLong())
            exchange.responseBody.use { it.write(json) }
        }
        server.createContext("/missing") { exchange -> exchange.sendResponseHeaders(404, -1); exchange.close() }
        server.start()
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            val found = ReleaseFetcher(OkHttpClient(), "$base/latest").check(1_000)
            assertEquals("v1.0", (found as ReleaseCheck.Found).release.tag)
            assertTrue(ReleaseFetcher(OkHttpClient(), "$base/missing").check(1_000) is ReleaseCheck.Unavailable)
        } finally {
            server.stop(0)
        }
    }
}
