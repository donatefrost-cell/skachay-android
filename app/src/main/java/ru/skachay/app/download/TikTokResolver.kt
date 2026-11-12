package ru.skachay.app.download

import java.io.IOException
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import ru.skachay.app.network.UrlNetworkPolicy
import ru.skachay.app.network.readUtf8Bounded
import ru.skachay.app.recipes.RecipeRegistry

class TikTokResolver : MediaResolver {
    private val cookies = SessionCookies()
    private val client = UrlNetworkPolicy.publicHttpsClient(
        callTimeoutSeconds = 30,
        allowedRedirectHostSuffixes = setOf("tiktok.com"),
    )
        .newBuilder().cookieJar(cookies).build()

    override fun resolve(url: String): List<ResolvedMedia> {
        if (!TikTokLink.supports(url)) return emptyList()
        val recipe = RecipeRegistry.current.tiktok
        if (!recipe.pageEnabled) return emptyList()
        val request = Request.Builder().url(url.toHttps()).header("User-Agent", recipe.mobileAgent)
            .header("Referer", recipe.referer).build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val finalUrl = response.request.url.toString()
                if (!TikTokLink.supports(finalUrl)) return emptyList()
                val id = TikTokLink.postId(finalUrl) ?: TikTokLink.postId(url) ?: return emptyList()
                val html = response.body.readUtf8Bounded(MAX_HTML_BYTES) ?: return emptyList()
                TikTokParser.parse(html, id, recipe).map { item ->
                    val headers = mutableMapOf("User-Agent" to recipe.mobileAgent, "Referer" to finalUrl)
                    val cookieHeader = item.url.toHttpUrlOrNull()?.let(cookies::headerFor)
                    if (!cookieHeader.isNullOrEmpty()) headers["Cookie"] = cookieHeader
                    item.copy(headers = headers)
                }
            }
        } catch (_: IOException) {
            emptyList()
        }
    }

    private class SessionCookies : CookieJar {
        private val values = mutableListOf<Cookie>()

        @Synchronized
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            for (cookie in cookies) {
                values.removeAll { it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path }
                if (cookie.expiresAt > System.currentTimeMillis()) values.add(cookie)
            }
        }

        @Synchronized
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            values.removeAll { it.expiresAt <= System.currentTimeMillis() }
            return values.filter { it.matches(url) }
        }

        fun headerFor(url: HttpUrl): String = loadForRequest(url).joinToString("; ") { "${it.name}=${it.value}" }
    }

    companion object {
        private const val MAX_HTML_BYTES = 5 * 1024 * 1024
    }
}

private fun String.toHttps(): String = replaceFirst(Regex("^http://", RegexOption.IGNORE_CASE), "https://")
