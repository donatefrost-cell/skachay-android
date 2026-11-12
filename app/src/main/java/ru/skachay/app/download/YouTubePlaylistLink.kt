package ru.skachay.app.download

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

internal object YouTubePlaylistLink {
    fun isPurePlaylist(url: String): Boolean {
        if (!SourceType.isYouTube(url)) return false
        val uri =
            try {
                URI(url)
            } catch (_: Exception) {
                return false
            }
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        if (host == "youtu.be") return false
        val path = uri.path.orEmpty().lowercase(Locale.ROOT)
        val isVideoPath =
            Regex("/(?:shorts|live|v)/[^/]+").containsMatchIn(path) ||
                (Regex("/embed/[^/]+").containsMatchIn(path) && !path.contains("/embed/videoseries"))
        if (isVideoPath) return false
        val query =
            try {
                uri.rawQuery.orEmpty().split('&').mapNotNull { part ->
                    val key = URLDecoder.decode(part.substringBefore('='), StandardCharsets.UTF_8.name())
                    val value = URLDecoder.decode(part.substringAfter('=', ""), StandardCharsets.UTF_8.name())
                    key to value
                }
            } catch (_: IllegalArgumentException) {
                return false
            }
        val hasList = query.any { (key, value) -> key == "list" && value.isNotBlank() }
        val hasVideoId = query.any { (key, value) -> key == "v" && value.isNotBlank() }
        return hasList && !hasVideoId
    }
}
