package ru.skachay.app.download

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object FileNameTemplate {
    private val token = Regex("\\{([a-z]+)\\}")

    fun resolve(
        template: String?,
        title: String?,
        url: String,
        source: String,
        index: Int? = null,
        date: String = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date()),
    ): String? {
        if (template.isNullOrBlank() || template.length > MAX_TEMPLATE_LENGTH) return null
        val fallbackTitle =
            title?.takeIf { it.isNotBlank() }
                ?: url.substringAfterLast('/').substringBefore('?').ifBlank { source }
        val values = mapOf("title" to fallbackTitle, "source" to source, "date" to date, "index" to index?.toString().orEmpty())
        val expanded = token.replace(template) { match -> values[match.groupValues[1]] ?: match.value }
        return SavedFileName.clean(expanded)
    }

    private const val MAX_TEMPLATE_LENGTH = 1024
}
