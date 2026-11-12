package ru.skachay.app.download

import okhttp3.OkHttpClient
import okhttp3.Request
import ru.skachay.app.network.UrlNetworkPolicy
import ru.skachay.app.network.readUtf8Bounded
import ru.skachay.app.recipes.RecipeRegistry
import java.io.IOException

class InstagramResolver(
    private val client: OkHttpClient =
        UrlNetworkPolicy.publicHttpsClient(
            callTimeoutSeconds = 30,
            allowedRedirectHostSuffixes = setOf("instagram.com"),
        ),
) : MediaResolver {
    override fun resolve(url: String): List<ResolvedMedia> {
        if (!InstagramLink.supports(url)) return emptyList()
        val recipe = RecipeRegistry.current.instagram
        if (!recipe.embedEnabled && !recipe.pageEnabled) return emptyList()
        val secureUrl = url.toHttps()
        val code =
            InstagramLink.shortcode(secureUrl) ?: follow(secureUrl, recipe.mobileAgent)?.let(InstagramLink::shortcode) ?: return emptyList()
        val embed = recipe.embedTemplate.replace("{shortcode}", code)
        val page = recipe.pageTemplate.replace("{shortcode}", code)
        val attempts =
            buildList {
                if (recipe.embedEnabled) {
                    add(embed to recipe.mobileAgent)
                    add(embed to recipe.facebookAgent)
                }
                if (recipe.pageEnabled) {
                    add(page to recipe.mobileAgent)
                    add(page to recipe.iosAgent)
                }
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

    private fun follow(
        url: String,
        agent: String,
    ): String? =
        try {
            val request =
                Request
                    .Builder()
                    .url(url)
                    .header("User-Agent", agent)
                    .build()
            client.newCall(request).execute().use { response ->
                response.request.url
                    .toString()
                    .takeIf(InstagramLink::supports)
            }
        } catch (_: IOException) {
            null
        }

    private fun get(
        url: String,
        agent: String,
    ): String? {
        return try {
            val request =
                Request
                    .Builder()
                    .url(url.toHttps())
                    .header("User-Agent", agent)
                    .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body.readUtf8Bounded(MAX_HTML_BYTES)
            }
        } catch (_: IOException) {
            null
        }
    }

    companion object {
        private const val MAX_HTML_BYTES = 5 * 1024 * 1024
    }
}

private fun String.toHttps(): String = replaceFirst(Regex("^http://", RegexOption.IGNORE_CASE), "https://")
