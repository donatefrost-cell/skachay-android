package ru.skachay.app.download

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

object SourceType {
    fun isYouTube(url: String): Boolean {
        val uri = try {
            URI(url)
        } catch (_: URISyntaxException) {
            return false
        }
        if (uri.scheme?.lowercase(Locale.ROOT) !in listOf("http", "https")) return false
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        return host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com")
    }
}
