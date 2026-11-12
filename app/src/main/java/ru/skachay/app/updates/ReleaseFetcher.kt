package ru.skachay.app.updates

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.skachay.app.network.UrlNetworkPolicy
import ru.skachay.app.network.readUtf8Bounded
import java.io.IOException

sealed interface ReleaseCheck {
    data class Found(
        val release: ReleaseInfo,
    ) : ReleaseCheck

    data object UpToDate : ReleaseCheck

    data object Unavailable : ReleaseCheck
}

internal fun interface ReleaseSource {
    fun read(): String?
}

internal class ReleaseFetcher(
    private val source: ReleaseSource = GithubReleaseSource(),
) {
    fun check(currentVersion: Int): ReleaseCheck {
        val json =
            try {
                source.read()
            } catch (_: IOException) {
                null
            } ?: return ReleaseCheck.Unavailable
        val release = ReleaseParser.parse(json, 0) ?: return ReleaseCheck.Unavailable
        return if (release.versionCode > currentVersion) ReleaseCheck.Found(release) else ReleaseCheck.UpToDate
    }

    companion object {
        const val API_URL = "https://api.github.com/repos/donatefrost-cell/skachay-android/releases/latest"
        internal const val MAX_JSON_BYTES = 262_144

        internal fun isAllowedEndpoint(value: String): Boolean {
            val endpoint = value.toHttpUrlOrNull() ?: return false
            return isAllowedEndpoint(endpoint)
        }

        private fun isAllowedEndpoint(endpoint: HttpUrl): Boolean =
            endpoint.host == "api.github.com" &&
                endpoint.encodedPath == "/repos/donatefrost-cell/skachay-android/releases/latest" &&
                endpoint.query == null &&
                endpoint.fragment == null &&
                endpoint.username.isEmpty() &&
                endpoint.password.isEmpty() &&
                UrlNetworkPolicy.isSafeHttpsUrl(endpoint)
    }
}

private class GithubReleaseSource : ReleaseSource {
    private val client: OkHttpClient =
        UrlNetworkPolicy.publicHttpsClient(
            callTimeoutSeconds = 30,
            allowedRedirectHostSuffixes = setOf("api.github.com"),
        )

    override fun read(): String? {
        if (!ReleaseFetcher.isAllowedEndpoint(ReleaseFetcher.API_URL)) return null
        val request =
            Request
                .Builder()
                .url(ReleaseFetcher.API_URL)
                .header("User-Agent", "Skachay/0.1 Android")
                .header("Accept", "application/vnd.github+json")
                .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body.readUtf8Bounded(ReleaseFetcher.MAX_JSON_BYTES)
        }
    }
}
