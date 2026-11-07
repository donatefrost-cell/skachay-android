package ru.skachay.app.diagnostics

import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import ru.skachay.app.download.InstagramLink
import ru.skachay.app.download.SourceType
import ru.skachay.app.download.TikTokLink

internal enum class DownloadProvider(val label: String) {
    YOUTUBE("YouTube"), INSTAGRAM("Instagram"), TIKTOK("TikTok"), OTHER("Другие ссылки"),
}

internal enum class DiagnosticStatus(val label: String) {
    NEVER("Ещё не проверялось"), RUNNING("Скачивается"), FINISHED("Готово"),
    ALREADY("Уже скачано"), FAILED("Не получилось скачать"), INTERRUPTED("Загрузка прервана"),
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
)

internal fun providerFor(url: String): DownloadProvider = when {
    InstagramLink.supports(url) -> DownloadProvider.INSTAGRAM
    TikTokLink.supports(url) -> DownloadProvider.TIKTOK
    SourceType.isYouTube(url) -> DownloadProvider.YOUTUBE
    else -> DownloadProvider.OTHER
}

internal fun diagnosticError(provider: DownloadProvider, error: Throwable): String {
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

internal fun diagnosticLog(entries: List<DiagnosticEntry>): String = buildString {
    appendLine("Скачай — диагностика")
    val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.forLanguageTag("ru"))
    entries.forEach { entry ->
        appendLine()
        appendLine("${entry.provider.label}: ${entry.status.label}")
        if (entry.timestamp > 0) appendLine("Последняя попытка: ${dateFormat.format(Date(entry.timestamp))}")
        entry.lastError?.let { appendLine("Последняя ошибка: $it") }
        DownloadStage.entries.forEach { stage ->
            val duration = entry.durations[stage]?.let { String.format(Locale.forLanguageTag("ru"), "%.1f с", it / 1000.0) } ?: "нет данных"
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
}.trimEnd()

internal fun formatSpeed(bytesPerSecond: Long): String = String.format(
    Locale.forLanguageTag("ru"), "%.1f МБ/с", bytesPerSecond.coerceAtLeast(0) / 1_048_576.0,
)

internal fun formatDuration(millis: Long): String {
    val seconds = (millis.coerceAtLeast(0) + 999) / 1_000
    return if (seconds >= 60) "${seconds / 60} мин ${seconds % 60} с" else "$seconds с"
}

internal fun formatBytes(bytes: Long): String = String.format(
    Locale.forLanguageTag("ru"), "%.1f МБ", bytes.coerceAtLeast(0) / 1_048_576.0,
)
