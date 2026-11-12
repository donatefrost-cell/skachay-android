package ru.skachay.app.download

import ru.skachay.app.network.UrlNetworkPolicy

/** Shared connection pool for media byte transfers. OkHttp negotiates HTTP/2 when the host supports it. */
internal object MediaDownloadClient {
    val client = UrlNetworkPolicy.publicHttpsClient(callTimeoutSeconds = 6 * 60 * 60)

    val downloader: DirectDownloader = DirectDownloader(client)

    private val restrictedDownloaders = mutableMapOf<Set<String>, DirectDownloader>()

    @Synchronized
    fun forSource(sourceUrl: String): DirectDownloader? {
        val suffixes = ResolvedMediaNetworkPolicy.redirectHostSuffixes(sourceUrl) ?: return null
        return restrictedDownloaders.getOrPut(suffixes) {
            DirectDownloader(
                UrlNetworkPolicy.publicHttpsClient(
                    callTimeoutSeconds = 6 * 60 * 60,
                    allowedRedirectHostSuffixes = suffixes,
                ),
            )
        }
    }
}
