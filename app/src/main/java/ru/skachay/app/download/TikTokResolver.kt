package ru.skachay.app.download

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.skachay.app.recipes.RecipeRegistry

class TikTokResolver : MediaResolver {
    private val cookies = SessionCookies()
    private val client = OkHttpClient.Builder().cookieJar(cookies).callTimeout(30, TimeUnit.SECONDS).build()

    override fun resolve(url: String): List<ResolvedMedia> {
        if (!TikTokLink.supports(url)) return emptyList()
        val recipe = RecipeRegistry.current.tiktok
        if (!recipe.pageEnabled) return emptyList()
        val request = Request.Builder().url(url).header("User-Agent", recipe.mobileAgent)
            .header("Referer", recipe.referer).build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val finalUrl = response.request.url.toString()
                if (!TikTokLink.supports(finalUrl)) return emptyList()
                val id = TikTokLink.postId(finalUrl) ?: TikTokLink.postId(url) ?: return emptyList()
                val html = response.body.charStream().use { reader ->
                    val text = StringBuilder()
                    val buffer = CharArray(8192)
                    while (text.length <= MAX_HTML) {
                        val count = reader.read(buffer)
                        if (count < 0) break
                        text.append(buffer, 0, count)
                    }
                    if (text.length > MAX_HTML) return emptyList()
                    text.toString()
                }
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
        private const val MAX_HTML = 4_000_000
    }
}
