package ru.skachay.app

import java.net.URI

object SharedUrl {
    private val linkPattern = Regex("https?://[^\\s<>\"']+", RegexOption.IGNORE_CASE)

    fun firstIn(text: String?): String? = allIn(text).firstOrNull()

    fun allIn(text: String?): List<String> {
        if (text == null) return emptyList()
        return linkPattern.findAll(text).mapNotNull { match ->
            val candidate = trimPunctuation(match.value)
            val uri = try {
                URI(candidate)
            } catch (_: IllegalArgumentException) {
                return@mapNotNull null
            } catch (_: java.net.URISyntaxException) {
                return@mapNotNull null
            }
            candidate.takeIf { uri.host != null && uri.userInfo == null }
        }.distinct().toList()
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
