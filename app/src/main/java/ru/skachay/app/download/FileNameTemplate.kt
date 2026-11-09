package ru.skachay.app.download

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object FileNameTemplate {
    fun resolve(
        template: String?,
        title: String?,
        url: String,
        source: String,
        index: Int? = null,
        date: String = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date()),
    ): String? {
        if (template.isNullOrBlank()) return null
        val fallbackTitle = title?.takeIf { it.isNotBlank() }
            ?: url.substringAfterLast('/').substringBefore('?').ifBlank { source }
        val expanded = template
            .replace("{title}", fallbackTitle)
            .replace("{source}", source)
            .replace("{date}", date)
            .replace("{index}", index?.toString().orEmpty())
        return SavedFileName.clean(expanded)
    }
}
