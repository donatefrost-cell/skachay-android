package ru.skachay.app.download

import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.skachay.app.recipes.RecipeRegistry

class InstagramResolver(private val client: OkHttpClient = OkHttpClient()) : MediaResolver {
    override fun resolve(url: String): List<ResolvedMedia> {
        if (!InstagramLink.supports(url)) return emptyList()
        val recipe = RecipeRegistry.current.instagram
        if (!recipe.embedEnabled && !recipe.pageEnabled) return emptyList()
        val code = InstagramLink.shortcode(url) ?: follow(url, recipe.mobileAgent)?.let(InstagramLink::shortcode) ?: return emptyList()
        val embed = recipe.embedTemplate.replace("{shortcode}", code)
        val page = recipe.pageTemplate.replace("{shortcode}", code)
        val attempts = buildList {
            if (recipe.embedEnabled) { add(embed to recipe.mobileAgent); add(embed to recipe.facebookAgent) }
            if (recipe.pageEnabled) { add(page to recipe.mobileAgent); add(page to recipe.iosAgent) }
        }
        for ((address, agent) in attempts) {
            val html = get(address, agent) ?: continue
            val media = InstagramParser.parse(html, requireVideo = InstagramLink.isVideoPost(url), recipe = recipe)
            if (media.isNotEmpty()) {
                val headers = mapOf("User-Agent" to agent, "Referer" to "https://www.instagram.com/")
                return media.map { it.copy(headers = headers) }
            }
        }
        return emptyList()
    }

    private fun follow(url: String, agent: String): String? = try {
        val request = Request.Builder().url(url).header("User-Agent", agent).build()
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
    }
}
