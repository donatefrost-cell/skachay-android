package ru.skachay.app.download

import com.google.re2j.Pattern
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale
import ru.skachay.app.recipes.RecipeRegistry

object InstagramLink {
    fun canonical(url: String): String? = shortcode(url)?.let { "https://www.instagram.com/p/$it/" }

    fun isVideoPost(url: String): Boolean {
        if (!supports(url)) return false
        val path = parse(url)?.rawPath ?: return false
        return path.length <= 2048 && Pattern.compile(RecipeRegistry.current.instagram.videoPattern).matcher(path).matches()
    }

    fun supports(url: String): Boolean {
        val uri = parse(url) ?: return false
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        return host == "instagram.com" || host.endsWith(".instagram.com") ||
            host == "instagr.am" || host.endsWith(".instagr.am") || host == "ig.me"
    }

    fun shortcode(url: String): String? {
        if (!supports(url)) return null
        val path = parse(url)?.rawPath ?: return null
        if (path.length > 2048) return null
        val match = Pattern.compile(RecipeRegistry.current.instagram.postPattern).matcher(path)
        if (!match.matches()) return null
        return match.group(1)?.takeIf { Regex("[A-Za-z0-9_-]{1,128}").matches(it) }
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
