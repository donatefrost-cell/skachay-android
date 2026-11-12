package ru.skachay.app.download

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

object SourceType {
    fun isYouTube(url: String): Boolean {
        if (url.length > MAX_URL_LENGTH) return false
        val uri = try {
            URI(url)
        } catch (_: URISyntaxException) {
            return false
        }
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme !in listOf("http", "https") || uri.userInfo != null ||
            uri.port !in setOf(-1, if (scheme == "http") 80 else 443)) return false
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        return host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com")
    }

    private const val MAX_URL_LENGTH = 2048
}
