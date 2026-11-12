package ru.skachay.app.updates

import org.json.JSONException
import org.json.JSONObject
import java.net.URI
import java.net.URISyntaxException

data class ReleaseInfo(
    val tag: String,
    val versionCode: Int,
    val downloadUrl: String,
    val sha256: String,
)

object ReleaseParser {
    fun parse(
        json: String,
        currentVersion: Int,
    ): ReleaseInfo? {
        if (json.toByteArray(Charsets.UTF_8).size > 262_144) return null
        return try {
            val release = JSONObject(json)
            if (release.get("draft") != false || release.get("prerelease") != false) return null
            val tag = release.get("tag_name") as? String ?: return null
            val version = versionCode(tag) ?: return null
            if (version <= currentVersion) return null
            val assets = release.getJSONArray("assets")
            if (assets.length() > 100) return null
            for (index in 0 until assets.length()) {
                val asset = assets.optJSONObject(index) ?: continue
                if (asset.optString("name") != "app-release.apk") continue
                val url = asset.optString("browser_download_url")
                val digest = parseSha256(asset.optString("digest")) ?: continue
                if (trustedUrl(url, tag)) return ReleaseInfo(tag, version, url, digest)
            }
            null
        } catch (_: JSONException) {
            null
        }
    }

    fun versionCode(tag: String): Int? {
        val match = Regex("^v(\\d{1,4})\\.(\\d{1,3})(?:\\.(\\d{1,3}))?$").matchEntire(tag) ?: return null
        val major = match.groupValues[1].toLong()
        val minor = match.groupValues[2].toLong()
        val patch = match.groupValues[3].ifEmpty { "0" }.toLong()
        val value = major * 1_000_000 + minor * 1_000 + patch
        return value.toInt().takeIf { major <= 2099 && value in 1..2_100_000_000L }
    }

    internal fun stored(
        tag: String,
        url: String,
        digest: String?,
        currentVersion: Int,
    ): ReleaseInfo? {
        val version = versionCode(tag) ?: return null
        val sha256 = parseSha256(digest) ?: return null
        return if (version > currentVersion && trustedUrl(url, tag)) ReleaseInfo(tag, version, url, sha256) else null
    }

    private fun parseSha256(value: String?): String? =
        value
            ?.removePrefix("sha256:")
            ?.takeIf { Regex("[A-Fa-f0-9]{64}").matches(it) }
            ?.lowercase()

    private fun trustedUrl(
        value: String,
        tag: String,
    ): Boolean {
        val uri =
            try {
                URI(value)
            } catch (_: URISyntaxException) {
                return false
            }
        return uri.scheme == "https" && uri.host == "github.com" && uri.port in listOf(-1, 443) && uri.userInfo == null &&
            uri.rawPath == "/donatefrost-cell/skachay-android/releases/download/$tag/app-release.apk" &&
            uri.query == null && uri.fragment == null
    }
}
