package ru.skachay.app.download

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

object TikTokLink {
    fun supports(url: String): Boolean {
        val uri = parse(url) ?: return false
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        return host == "tiktok.com" || host.endsWith(".tiktok.com")
    }

    fun postId(url: String): String? {
        if (!supports(url)) return null
        return Regex("/(?:video|photo)/(\\d+)(?:/|$)").find(parse(url)?.rawPath.orEmpty())?.groupValues?.get(1)
    }

    fun isPhoto(url: String): Boolean = supports(url) &&
        Regex("/photo/\\d+(?:/|$)").containsMatchIn(parse(url)?.rawPath.orEmpty())

    private fun parse(url: String): URI? {
        val uri = try { URI(url) } catch (_: URISyntaxException) { return null }
        return uri.takeIf { it.scheme?.lowercase(Locale.ROOT) in listOf("http", "https") && it.userInfo == null }
    }
}
