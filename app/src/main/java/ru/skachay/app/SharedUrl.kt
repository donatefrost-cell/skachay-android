package ru.skachay.app

import java.net.URI

object SharedUrl {
    private val linkPattern = Regex("https?://[^\\s<>\"']+", RegexOption.IGNORE_CASE)

    fun firstIn(text: String?): String? {
        if (text == null) return null
        for (match in linkPattern.findAll(text)) {
            val candidate = trimPunctuation(match.value)
            val uri = try {
                URI(candidate)
            } catch (_: IllegalArgumentException) {
                continue
            } catch (_: java.net.URISyntaxException) {
                continue
            }
            if (uri.host != null && uri.userInfo == null) return candidate
        }
        return null
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
}
