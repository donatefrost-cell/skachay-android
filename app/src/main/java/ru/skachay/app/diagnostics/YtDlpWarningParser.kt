package ru.skachay.app.diagnostics

internal object YtDlpWarningParser {
    private const val MAX_WARNINGS = 24
    private const val MAX_WARNING_LENGTH = 400

    private val warningMarker = Regex("(?i)(?:^|\\s)(?:warning:|\\[warning])")
    private val challengeSignal = Regex(
        "(?i)(javascript runtime|js runtimes?|n[ -]challenge|nsig|javascript challenge|challenge solver|challenge provider|signature.*(?:failed|error))",
    )
    private val url = Regex("""(?i)https?://[^\s\])}>"']+""")
    private val secret = Regex("""(?i)\b(proxy-authorization|authorization|cookie|set-cookie|access[_-]?token|token|api[_-]?key)\s*[:=]\s*[^\s,;]+""")

    fun parse(output: String): List<String> {
        val unique = linkedMapOf<String, String>()
        output.lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && (warningMarker.containsMatchIn(it) || challengeSignal.containsMatchIn(it)) }
            .map(::sanitize)
            .filter(String::isNotBlank)
            .forEach { warning ->
                unique.putIfAbsent(warning.lowercase(), warning)
                if (unique.size >= MAX_WARNINGS) return unique.values.toList()
            }
        return unique.values.toList()
    }

    private fun sanitize(value: String): String = value
        .replace(url, "[ссылка скрыта]")
        .replace(secret, "${'$'}1=[скрыто]")
        .take(MAX_WARNING_LENGTH)
}
