package ru.skachay.app.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket

class YtDlpHostPolicyTest {
    @Test
    fun youtubeMediaCdnIsAllowedButHostLookalikesAreRejected() {
        assertTrue(YtDlpHostPolicy.isAllowedConnectHost(YOUTUBE_URL, "rr2---sn.googlevideo.com", 443))
        assertTrue(YtDlpHostPolicy.isAllowedConnectHost(YOUTUBE_URL, "www.youtube.com", 443))
        assertFalse(YtDlpHostPolicy.isAllowedConnectHost(YOUTUBE_URL, "youtube.com.evil.example", 443))
        assertFalse(YtDlpHostPolicy.isAllowedConnectHost(YOUTUBE_URL, "evilyoutube.com", 443))
        assertFalse(YtDlpHostPolicy.isAllowedConnectHost(YOUTUBE_URL, "googlevideo.com.evil.example", 443))
    }

    @Test
    fun youtubeEjsRemoteComponentHostsAreAllowedButLookalikesAreRejected() {
        assertTrue(YtDlpHostPolicy.isAllowedConnectHost(YOUTUBE_URL, "github.com", 443))
        assertTrue(YtDlpHostPolicy.isAllowedConnectHost(YOUTUBE_URL, "release-assets.githubusercontent.com", 443))
        assertFalse(YtDlpHostPolicy.isAllowedConnectHost(YOUTUBE_URL, "github.com.evil.example", 443))
        assertFalse(
            YtDlpHostPolicy.isAllowedConnectHost(YOUTUBE_URL, "release-assets.githubusercontent.com.evil.example", 443),
        )
        assertFalse(YtDlpHostPolicy.isAllowedConnectHost(INSTAGRAM_URL, "github.com", 443))
    }

    @Test
    fun proxyRejectsCleartextPortsAndUnsupportedSources() {
        assertFalse(YtDlpHostPolicy.isAllowedConnectHost(YOUTUBE_URL, "www.youtube.com", 80))
        assertFalse(YtDlpHostPolicy.isAllowedConnectHost("https://example.com/video", "example.com", 443))
    }

    @Test
    fun socialSourceCanOnlyReachItsKnownCdnDomains() {
        assertTrue(YtDlpHostPolicy.isAllowedConnectHost(INSTAGRAM_URL, "scontent.cdninstagram.com", 443))
        assertFalse(YtDlpHostPolicy.isAllowedConnectHost(INSTAGRAM_URL, "cdninstagram.com.evil.example", 443))
        assertFalse(YtDlpHostPolicy.isAllowedConnectHost(INSTAGRAM_URL, "evilyinstagram.com", 443))
    }

    @Test
    fun guardedProxyRejectsLoopbackAndHostLookalikeConnectRequests() {
        GuardedYtDlpProxy(YOUTUBE_URL).use { proxy ->
            val port = java.net.URI(proxy.url).port
            listOf("127.0.0.1:443", "youtube.com.evil.example:443").forEach { target ->
                Socket("127.0.0.1", port).use { socket ->
                    socket.soTimeout = 2_000
                    OutputStreamWriter(socket.getOutputStream(), Charsets.US_ASCII).apply {
                        write("CONNECT $target HTTP/1.1\r\nHost: $target\r\n\r\n")
                        flush()
                    }
                    val status = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.US_ASCII)).readLine()
                    assertTrue("unexpected proxy response for $target: $status", status.startsWith("HTTP/1.1 403"))
                }
            }
        }
    }

    private companion object {
        const val YOUTUBE_URL = "https://www.youtube.com/watch?v=sample"
        const val INSTAGRAM_URL = "https://www.instagram.com/reel/sample/"
    }
}
