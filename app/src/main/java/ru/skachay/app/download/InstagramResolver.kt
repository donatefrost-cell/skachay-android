package ru.skachay.app.download

import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request

class InstagramResolver(private val client: OkHttpClient = OkHttpClient()) : MediaResolver {
    override fun resolve(url: String): List<ResolvedMedia> {
        if (!InstagramLink.supports(url)) return emptyList()
        val code = InstagramLink.shortcode(url) ?: follow(url)?.let(InstagramLink::shortcode) ?: return emptyList()
        val embed = "https://www.instagram.com/p/$code/embed/captioned/"
        val page = "https://www.instagram.com/p/$code/"
        val attempts = listOf(embed to MOBILE, embed to FACEBOOK, page to MOBILE, page to IOS)
        for ((address, agent) in attempts) {
            val html = get(address, agent) ?: continue
            val media = InstagramParser.parse(html, requireVideo = InstagramLink.isVideoPost(url))
            if (media.isNotEmpty()) {
                val headers = mapOf("User-Agent" to agent, "Referer" to "https://www.instagram.com/")
                return media.map { it.copy(headers = headers) }
            }
        }
        return emptyList()
    }

    private fun follow(url: String): String? = try {
        val request = Request.Builder().url(url).header("User-Agent", MOBILE).build()
        client.newCall(request).execute().use { response ->
            response.request.url.toString().takeIf(InstagramLink::supports)
        }
    } catch (_: IOException) {
        null
    }

    private fun get(url: String, agent: String): String? {
        return try {
            val request = Request.Builder().url(url).header("User-Agent", agent).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body.charStream().use { reader ->
                    val output = StringBuilder()
                    val buffer = CharArray(8192)
                    while (output.length <= MAX_HTML) {
                        val count = reader.read(buffer)
                        if (count < 0) return output.toString()
                        output.append(buffer, 0, count)
                    }
                    null
                }
            }
        } catch (_: IOException) {
            null
        }
    }

    companion object {
        private const val MAX_HTML = 2_000_000
        private const val MOBILE = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36"
        private const val FACEBOOK = "facebookexternalhit/1.1 (+https://www.facebook.com/externalhit_uatext.php)"
        private const val IOS = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Version/17.0 Mobile/15E148 Safari/604.1"
    }
}
