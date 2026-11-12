package ru.skachay.app

import java.net.URI
import java.util.Locale

object SharedUrl {
    private val linkPattern = Regex("https?://[^\\s<>\"']+", RegexOption.IGNORE_CASE)

    fun firstIn(text: String?): String? = allIn(text).firstOrNull()

    fun allIn(text: String?): List<String> {
        if (text == null || text.length > MAX_SHARE_TEXT_LENGTH) return emptyList()
        return linkPattern.findAll(text).mapNotNull { match ->
            val candidate = trimPunctuation(match.value)
            if (candidate.length > MAX_URL_LENGTH) return@mapNotNull null
            val uri = try {
                URI(candidate)
            } catch (_: IllegalArgumentException) {
                return@mapNotNull null
            } catch (_: java.net.URISyntaxException) {
                return@mapNotNull null
            }
            candidate.takeIf { isSupportedUrl(uri) }?.let(::upgradeToHttps)
        }.distinct().take(MAX_LINKS).toList()
    }

    private fun isSupportedUrl(uri: URI): Boolean {
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme !in setOf("http", "https") || uri.userInfo != null) return false
        if (uri.port !in setOf(-1, if (scheme == "http") 80 else 443)) return false
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        return host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com") ||
            host == "instagram.com" || host.endsWith(".instagram.com") ||
            host == "instagr.am" || host.endsWith(".instagr.am") || host == "ig.me" ||
            host == "tiktok.com" || host.endsWith(".tiktok.com")
    }

    private fun upgradeToHttps(value: String): String {
        val secure = value.replaceFirst(Regex("^http://", RegexOption.IGNORE_CASE), "https://")
        return secure.replaceFirst(Regex("^(https://[^/?#]+):80(?=[/?#]|$)", RegexOption.IGNORE_CASE), "$1")
    }

    private fun trimPunctuation(value: String): String {
        var result = value.trimEnd('.', ',', '!', '?', ':', ';')
        val brackets = listOf('(' to ')', '[' to ']', '{' to '}')
        while (result.isNotEmpty()) {
            val pair = brackets.firstOrNull { it.second == result.last() } ?: break
            if (result.count { it == pair.second } <= result.count { it == pair.first }) break
            result = result.dropLast(1).trimEnd('.', ',', '!', '?', ':', ';')
        }
        return result
    }

    private const val MAX_URL_LENGTH = 2048
    private const val MAX_LINKS = 20
    private const val MAX_SHARE_TEXT_LENGTH = 65_536
}
