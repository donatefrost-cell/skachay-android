package ru.skachay.app.download

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import ru.skachay.app.network.UrlNetworkPolicy

/** Keeps extracted media requests on the CDN domains used by the source platform. */
internal object ResolvedMediaNetworkPolicy {
    private val youtubeHosts = setOf("googlevideo.com")
    private val instagramHosts = setOf("instagram.com", "cdninstagram.com", "fbcdn.net")
    private val tikTokHosts = setOf(
        "tiktok.com",
        "tiktokcdn.com",
        "tiktokcdn-us.com",
        "tiktokcdn-eu.com",
        "tiktokv.com",
        "byteoversea.com",
        "ibytedtos.com",
        "muscdn.com",
        "byteimg.com",
        "byteicdn.com",
    )

    fun redirectHostSuffixes(sourceUrl: String): Set<String>? = when {
        SourceType.isYouTube(sourceUrl) -> youtubeHosts
        InstagramLink.supports(sourceUrl) -> instagramHosts
        TikTokLink.supports(sourceUrl) -> tikTokHosts
        else -> null
    }

    fun isAllowed(sourceUrl: String, mediaUrl: String): Boolean {
        val allowedHosts = redirectHostSuffixes(sourceUrl) ?: return false
        val url = mediaUrl.toHttpUrlOrNull() ?: return false
        return UrlNetworkPolicy.isSafeHttpsUrl(url) && allowedHosts.any { UrlNetworkPolicy.hostMatchesSuffix(url.host, it) }
    }
}
