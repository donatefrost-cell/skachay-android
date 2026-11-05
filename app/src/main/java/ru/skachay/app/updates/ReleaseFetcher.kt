package ru.skachay.app.updates

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.skachay.app.network.readUtf8Bounded

sealed interface ReleaseCheck {
    data class Found(val release: ReleaseInfo) : ReleaseCheck
    data object UpToDate : ReleaseCheck
    data object Unavailable : ReleaseCheck
}

internal class ReleaseFetcher(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false).callTimeout(30, TimeUnit.SECONDS).build(),
    private val url: String = "https://api.github.com/repos/donatefrost-cell/skachay-android/releases/latest",
) {
    fun check(currentVersion: Int): ReleaseCheck = try {
        val request = Request.Builder().url(url).header("User-Agent", "Skachay/0.1 Android")
            .header("Accept", "application/vnd.github+json").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return ReleaseCheck.Unavailable
            val json = response.body.readUtf8Bounded(262_144) ?: return ReleaseCheck.Unavailable
            val release = ReleaseParser.parse(json, 0) ?: return ReleaseCheck.Unavailable
            if (release.versionCode > currentVersion) ReleaseCheck.Found(release) else ReleaseCheck.UpToDate
        }
    } catch (_: IOException) {
        ReleaseCheck.Unavailable
    }
}
