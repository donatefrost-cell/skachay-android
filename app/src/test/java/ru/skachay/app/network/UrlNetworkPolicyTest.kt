package ru.skachay.app.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class UrlNetworkPolicyTest {
    @Test
    fun rejectsNonPublicIpv4AndIpv6Addresses() {
        listOf(
            "0.1.2.3",
            "10.0.0.1",
            "100.64.0.1",
            "127.0.0.1",
            "169.254.1.1",
            "172.16.0.1",
            "192.168.1.1",
            "198.18.0.1",
            "224.0.0.1",
            "192.0.0.8",
            "::",
            "::1",
            "fe80::1",
            "fc00::1",
            "ff02::1",
            "2001:db8::1",
            "2001::1",
        ).forEach { address ->
            assertFalse("$address must not be public", UrlNetworkPolicy.isPublicAddress(InetAddress.getByName(address)))
        }
        assertTrue(UrlNetworkPolicy.isPublicAddress(InetAddress.getByName("8.8.8.8")))
        assertTrue(UrlNetworkPolicy.isPublicAddress(InetAddress.getByName("2606:4700:4700::1111")))
    }

    @Test
    fun allowsOnlyHttpsDefaultPortRedirectTargetsWithoutCredentials() {
        val source = "https://github.com/releases/file.apk".toHttpUrl()
        val cdn = UrlNetworkPolicy.resolveRedirect(source, "https://release-assets.githubusercontent.com/file.apk", 0)
        assertNotNull(cdn)
        assertEquals("release-assets.githubusercontent.com", cdn?.host)
        assertNull(UrlNetworkPolicy.resolveRedirect(source, "http://cdn.example/file", 0))
        assertNull(UrlNetworkPolicy.resolveRedirect(source, "https://user@cdn.example/file", 0))
        assertNull(UrlNetworkPolicy.resolveRedirect(source, "https://cdn.example:8443/file", 0))
        assertNull(UrlNetworkPolicy.resolveRedirect(source, "https://127.0.0.1/file", 0))
    }

    @Test
    fun capsRedirectChainAtFiveHops() {
        val source = "https://github.com/releases/file.apk".toHttpUrl()
        assertNotNull(UrlNetworkPolicy.resolveRedirect(source, "https://cdn.example/file", 4))
        assertNull(UrlNetworkPolicy.resolveRedirect(source, "https://cdn.example/file", 5))
    }

    @Test
    fun canRestrictRedirectsToAPlatformCdnSuffix() {
        val source = "https://github.com/releases/file.apk".toHttpUrl()
        val allowed = setOf("githubusercontent.com")
        assertNotNull(UrlNetworkPolicy.resolveRedirect(source, "https://release-assets.githubusercontent.com/file.apk", 0, allowed))
        assertNull(UrlNetworkPolicy.resolveRedirect(source, "https://githubusercontent.com.evil.test/file.apk", 0, allowed))
        assertNull(UrlNetworkPolicy.resolveRedirect(source, "https://evilgithubusercontent.com/file.apk", 0, allowed))
        assertTrue(UrlNetworkPolicy.hostMatchesSuffix("scontent.cdninstagram.com", "cdninstagram.com"))
        assertFalse(UrlNetworkPolicy.hostMatchesSuffix("instagram.com.evil.test", "instagram.com"))
        assertFalse(UrlNetworkPolicy.hostMatchesSuffix("evilinstagram.com", "instagram.com"))
    }
}
