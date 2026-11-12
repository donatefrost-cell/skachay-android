package ru.skachay.app.diagnostics

import ru.skachay.app.download.InstagramLink
import ru.skachay.app.download.SourceType
import ru.skachay.app.download.TikTokLink
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal enum class DownloadProvider(
    val label: String,
) {
    YOUTUBE("YouTube"),
    INSTAGRAM("Instagram"),
    TIKTOK("TikTok"),
    OTHER("Другие ссылки"),
}

internal enum class DiagnosticStatus(
    val label: String,
) {
    NEVER("Ещё не проверялось"),
    RUNNING("Скачивается"),
    FINISHED("Готово"),
    ALREADY("Уже скачано"),
    FAILED("Не получилось скачать"),
    INTERRUPTED("Загрузка прервана"),
}

internal const val MAX_DIAGNOSTIC_LOG_CHARS = 16_384

internal object DiagnosticTextRedactor {
    private val url = Regex("""(?i)https?://[^\s\])}>"']+""")
    private val header = Regex("""(?im)\b(proxy-authorization|authorization|cookie|set-cookie)\s*:\s*[^\r\n]*""")
    private val secret =
        Regex(
            """(?i)\b(proxy-authorization|authorization|cookie|set-cookie|access[_-]?token|token|api[_-]?key|signature|sig)\s*[:=]\s*[^\s,;]+""",
        )

    fun redact(value: String): String {
        val withoutHeaders = header.replace(value) { "${it.groupValues[1]}: [скрыто]" }
        val withoutAssignments = secret.replace(withoutHeaders) { "${it.groupValues[1]}=[скрыто]" }
        return url.replace(withoutAssignments, "[ссылка скрыта]")
    }
}

internal data class DiagnosticEntry(
    val provider: DownloadProvider,
    val status: DiagnosticStatus = DiagnosticStatus.NEVER,
    val timestamp: Long = 0,
    val lastError: String? = null,
    val durations: Map<DownloadStage, Long> = emptyMap(),
    val bytesReceived: Long? = null,
    val bytesTotal: Long? = null,
    val averageBytesPerSecond: Long? = null,
    val remainingMillis: Long? = null,
    val vpnActive: Boolean? = null,
    val engineWarnings: List<String> = emptyList(),
)

internal fun providerFor(url: String): DownloadProvider =
    when {
        InstagramLink.supports(url) -> DownloadProvider.INSTAGRAM
        TikTokLink.supports(url) -> DownloadProvider.TIKTOK
        SourceType.isYouTube(url) -> DownloadProvider.YOUTUBE
        else -> DownloadProvider.OTHER
    }

internal fun diagnosticError(
    provider: DownloadProvider,
    error: Throwable,
): String {
    val causes = generateSequence(error) { it.cause }.take(8)
    if (causes.any { it is UnknownHostException || it is SocketTimeoutException || it is SocketException }) {
        return "Не удалось подключиться. Проверьте интернет и попробуйте снова."
    }
    return when (provider) {
        DownloadProvider.INSTAGRAM -> "Публикация может быть закрыта, удалена или Instagram изменился. Проверьте ссылку."
        DownloadProvider.TIKTOK -> "Публикация может быть закрыта или недоступна. Проверьте ссылку."
        else -> "Не получилось скачать. Проверьте доступность ссылки и попробуйте снова."
    }
}

internal fun diagnosticLog(
    entries: List<DiagnosticEntry>,
    recentCrashes: List<String> = emptyList(),
): String =
    buildString {
        appendLine("Скачай — диагностика")
        recentCrashes.takeLast(5).forEach { appendLine("Ошибка приложения: ${DiagnosticTextRedactor.redact(it)}") }
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.forLanguageTag("ru"))
        entries.forEach { entry ->
            appendLine()
            appendLine("${entry.provider.label}: ${entry.status.label}")
            if (entry.timestamp > 0) appendLine("Последняя попытка: ${dateFormat.format(Date(entry.timestamp))}")
            entry.lastError?.let { appendLine("Последняя ошибка: ${DiagnosticTextRedactor.redact(it)}") }
            entry.engineWarnings.forEach { appendLine("Предупреждение yt-dlp: ${DiagnosticTextRedactor.redact(it)}") }
            DownloadStage.entries.forEach { stage ->
                val duration =
                    entry.durations[stage]?.let { String.format(Locale.forLanguageTag("ru"), "%.1f с", it / 1000.0) } ?: "нет данных"
                appendLine("${stage.label}: $duration")
            }
            entry.bytesReceived?.let { received ->
                val size = entry.bytesTotal?.let { " из ${formatBytes(it)}" }.orEmpty()
                appendLine("Получено: ${formatBytes(received)}$size")
            }
            entry.averageBytesPerSecond?.let { appendLine("Средняя скорость: ${formatSpeed(it)}") }
            entry.remainingMillis?.let { appendLine("Осталось: ${formatDuration(it)}") }
            entry.vpnActive?.let { appendLine("VPN: ${if (it) "активен" else "не активен"}") }
        }
    }.trimEnd().let { log ->
        if (log.length <= MAX_DIAGNOSTIC_LOG_CHARS) {
            log
        } else {
            log.take(MAX_DIAGNOSTIC_LOG_CHARS - LOG_TRUNCATED_MARKER.length).trimEnd() + LOG_TRUNCATED_MARKER
        }
    }

private const val LOG_TRUNCATED_MARKER = "\n… журнал сокращён"

internal fun formatSpeed(bytesPerSecond: Long): String =
    String.format(
        Locale.forLanguageTag("ru"),
        "%.1f МБ/с",
        bytesPerSecond.coerceAtLeast(0) / 1_048_576.0,
    )

internal fun formatDuration(millis: Long): String {
    val seconds = (millis.coerceAtLeast(0) + 999) / 1_000
    return if (seconds >= 60) "${seconds / 60} мин ${seconds % 60} с" else "$seconds с"
}

internal fun formatBytes(bytes: Long): String =
    String.format(
        Locale.forLanguageTag("ru"),
        "%.1f МБ",
        bytes.coerceAtLeast(0) / 1_048_576.0,
    )
