package ru.skachay.app.download

import com.google.re2j.Pattern
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale
import ru.skachay.app.recipes.RecipeRegistry

object TikTokLink {
    fun supports(url: String): Boolean {
        val uri = parse(url) ?: return false
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        return host == "tiktok.com" || host.endsWith(".tiktok.com")
    }

    fun postId(url: String): String? {
        if (!supports(url)) return null
        val path = parse(url)?.rawPath.orEmpty()
        if (path.length > 2048) return null
        val match = Pattern.compile(RecipeRegistry.current.tiktok.postPattern).matcher(path)
        if (!match.find()) return null
        return match.group(1)?.takeIf { Regex("[0-9]{1,32}").matches(it) }
    }

    fun isPhoto(url: String): Boolean {
        if (!supports(url)) return false
        val path = parse(url)?.rawPath.orEmpty()
        return path.length <= 2048 && Pattern.compile(RecipeRegistry.current.tiktok.photoPattern).matcher(path).find()
    }

    private fun parse(url: String): URI? {
        if (url.length > 2048) return null
        val uri = try { URI(url) } catch (_: URISyntaxException) { return null }
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        return uri.takeIf {
            scheme in listOf("http", "https") && it.userInfo == null &&
                it.port in setOf(-1, if (scheme == "http") 80 else 443)
        }
    }
}
