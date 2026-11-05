package ru.skachay.app.diagnostics

internal enum class DownloadStage(val label: String) {
    EXTRACTION("Получение сведений"),
    TRANSFER("Загрузка"),
    MERGE("Объединение"),
    SAVING("Сохранение"),
}

internal class StageTimer(private val now: () -> Long) {
    private val durations = mutableMapOf<DownloadStage, Long>()
    private var current: DownloadStage? = null
    private var started = 0L

    @Synchronized
    fun switchTo(stage: DownloadStage) {
        if (current == stage) return
        val time = now()
        current?.let { durations[it] = (durations[it] ?: 0) + (time - started).coerceAtLeast(0) }
        current = stage
        started = time
    }

    @Synchronized
    fun finish(): Map<DownloadStage, Long> {
        val time = now()
        current?.let { durations[it] = (durations[it] ?: 0) + (time - started).coerceAtLeast(0) }
        current = null
        return durations.toMap()
    }
}

internal fun stageFromYtDlpOutput(line: String): DownloadStage? = when {
    line.startsWith("[download]") -> DownloadStage.TRANSFER
    line.startsWith("[Merger]") || line.startsWith("[VideoRemuxer]") -> DownloadStage.MERGE
    else -> null
}
