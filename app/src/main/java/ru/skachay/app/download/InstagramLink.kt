package ru.skachay.app.download

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

object InstagramLink {
    fun canonical(url: String): String? = shortcode(url)?.let { "https://www.instagram.com/p/$it/" }

    fun isVideoPost(url: String): Boolean {
        if (!supports(url)) return false
        val path = parse(url)?.rawPath ?: return false
        return Regex("^/(?:[A-Za-z0-9._]+/)?(?:reel|reels|tv)/[A-Za-z0-9_-]+/?$").matches(path)
    }

    fun supports(url: String): Boolean {
        val uri = parse(url) ?: return false
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        return host == "instagram.com" || host.endsWith(".instagram.com") ||
            host == "instagr.am" || host.endsWith(".instagr.am") || host == "ig.me"
    }

    fun shortcode(url: String): String? {
        if (!supports(url)) return null
        val path = parse(url)?.rawPath ?: return null
        return Regex("^/(?:[A-Za-z0-9._]+/)?(?:p|reel|reels|tv)/([A-Za-z0-9_-]+)/?$")
            .matchEntire(path)?.groupValues?.get(1)
    }

    private fun parse(url: String): URI? {
        val uri = try { URI(url) } catch (_: URISyntaxException) { return null }
        return uri.takeIf { it.scheme?.lowercase(Locale.ROOT) in listOf("http", "https") && it.userInfo == null }
    }
}
