package ru.skachay.app.diagnostics

import android.content.Context
import androidx.core.content.edit
import java.util.UUID

internal object DiagnosticsStore {
    const val PREFERENCES = "diagnostics"
    private const val CRASH_KEY = "app.crashes"

    @Synchronized
    fun begin(
        context: Context,
        provider: DownloadProvider,
    ): String {
        val job = UUID.randomUUID().toString()
        val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        prefs
            .edit {
                putString(key(provider, "job"), job)
                putString(key(provider, "status"), DiagnosticStatus.RUNNING.name)
                putLong(key(provider, "timestamp"), System.currentTimeMillis())
                DownloadStage.entries.forEach { remove(key(provider, it.name)) }
                listOf(
                    "error",
                    "bytes",
                    "total",
                    "speed",
                    "remaining",
                    "vpn",
                    "warnings",
                    "technical",
                ).forEach { remove(key(provider, it)) }
            }
        return job
    }

    @Synchronized
    fun clearEngineWarnings(
        context: Context,
        provider: DownloadProvider,
    ) {
        context
            .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit {
                remove(key(provider, "warnings"))
            }
    }

    @Synchronized
    fun recordEngineWarnings(
        context: Context,
        provider: DownloadProvider,
        warnings: List<String>,
    ) {
        if (warnings.isEmpty()) return
        val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val existing =
            prefs
                .getString(key(provider, "warnings"), null)
                .orEmpty()
                .lines()
                .filter(String::isNotBlank)
                .map(DiagnosticTextRedactor::redact)
        val sanitized = warnings.map { DiagnosticTextRedactor.redact(it).take(MAX_WARNING_CHARS) }
        val combined = (existing + sanitized).distinctBy { it.lowercase() }.takeLast(24)
        prefs.edit { putString(key(provider, "warnings"), combined.joinToString("\n")) }
    }

    @Synchronized
    fun recordTechnicalFailure(
        context: Context,
        provider: DownloadProvider,
        details: String,
    ) {
        context
            .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit {
                putString(key(provider, "technical"), DiagnosticTextRedactor.redact(details).take(MAX_WARNING_CHARS))
            }
    }

    @Synchronized
    fun finish(
        context: Context,
        provider: DownloadProvider,
        job: String,
        status: DiagnosticStatus,
        durations: Map<DownloadStage, Long>,
        error: String? = null,
        transfer: TransferSnapshot? = null,
        vpnActive: Boolean? = null,
    ) {
        val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (prefs.getString(key(provider, "job"), null) != job) return
        prefs
            .edit {
                putString(key(provider, "status"), status.name)
                if (error != null) putString(key(provider, "error"), error) else remove(key(provider, "error"))
                durations.forEach { (stage, millis) -> putLong(key(provider, stage.name), millis) }
                transfer?.let {
                    putLong(key(provider, "bytes"), it.bytesReceived)
                    it.totalBytes?.let { total -> putLong(key(provider, "total"), total) }
                    it.averageBytesPerSecond?.let { speed -> putLong(key(provider, "speed"), speed) }
                    it.remainingMillis?.let { remaining -> putLong(key(provider, "remaining"), remaining) }
                    it.firstByteMillis?.let { firstByte -> putLong(key(provider, DownloadStage.FIRST_BYTE.name), firstByte) }
                }
                vpnActive?.let { putBoolean(key(provider, "vpn"), it) }
            }
    }

    @Synchronized
    fun checkpoint(
        context: Context,
        provider: DownloadProvider,
        job: String,
        transfer: TransferSnapshot,
        durations: Map<DownloadStage, Long>,
        vpnActive: Boolean,
    ) {
        val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (prefs.getString(key(provider, "job"), null) != job ||
            prefs.getString(key(provider, "status"), null) != DiagnosticStatus.RUNNING.name
        ) {
            return
        }
        prefs.edit {
            putLong(key(provider, "bytes"), transfer.bytesReceived)
            if (transfer.totalBytes != null) putLong(key(provider, "total"), transfer.totalBytes) else remove(key(provider, "total"))
            transfer.averageBytesPerSecond?.let { putLong(key(provider, "speed"), it) }
            transfer.firstByteMillis?.let { putLong(key(provider, DownloadStage.FIRST_BYTE.name), it) }
            durations.forEach { (stage, millis) -> putLong(key(provider, stage.name), millis) }
            putBoolean(key(provider, "vpn"), vpnActive)
        }
    }

    @Synchronized
    fun markInterrupted(context: Context) {
        val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        prefs.edit {
            DownloadProvider.entries.forEach { provider ->
                if (prefs.getString(key(provider, "status"), null) == DiagnosticStatus.RUNNING.name) {
                    putString(key(provider, "status"), DiagnosticStatus.INTERRUPTED.name)
                    putString(key(provider, "error"), "Загрузка прервалась. Попробуйте ещё раз.")
                }
            }
        }
    }

    @Synchronized
    fun recordUnhandledException(
        context: Context,
        thread: Thread,
        error: Throwable,
    ) {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val existing =
            preferences
                .getString(CRASH_KEY, null)
                .orEmpty()
                .lines()
                .filter(String::isNotBlank)
        val threadName = thread.name.filter { it.isLetterOrDigit() || it in "._- " }.take(64)
        val errorName =
            error.javaClass.simpleName
                .filter(Char::isLetterOrDigit)
                .take(80)
                .ifBlank { "Ошибка" }
        val details = runCatching { diagnosticTechnicalSummary(error).replace('\n', ' ').take(500) }.getOrDefault(errorName)
        val frames =
            (error.stackTrace.asSequence() + (error.cause?.stackTrace?.asSequence() ?: emptySequence()))
                .filter { it.className.startsWith("ru.skachay.") }
                .take(4)
                .joinToString(" ← ") { "${it.className}.${it.methodName}:${it.lineNumber}" }
        val entry =
            System.currentTimeMillis().toString() + " · " + threadName + " · " + details + if (frames.isBlank()) "" else " · $frames"
        preferences.edit(commit = true) { putString(CRASH_KEY, (existing + entry).takeLast(5).joinToString("\n")) }
    }

    fun recentUnhandledExceptions(context: Context): List<String> =
        context
            .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(CRASH_KEY, null)
            ?.lines()
            ?.filter(String::isNotBlank)
            ?.takeLast(5)
            .orEmpty()

    fun read(context: Context): List<DiagnosticEntry> {
        val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        return DownloadProvider.entries.map { provider ->
            val status =
                DiagnosticStatus.entries.firstOrNull { it.name == prefs.getString(key(provider, "status"), null) }
                    ?: DiagnosticStatus.NEVER
            val durations =
                DownloadStage.entries
                    .mapNotNull { stage ->
                        if (prefs.contains(key(provider, stage.name))) stage to prefs.getLong(key(provider, stage.name), 0) else null
                    }.toMap()
            DiagnosticEntry(
                provider,
                status,
                prefs.getLong(key(provider, "timestamp"), 0),
                prefs.getString(key(provider, "error"), null),
                durations,
                prefs.getLong(key(provider, "bytes"), -1L).takeIf { prefs.contains(key(provider, "bytes")) },
                prefs.getLong(key(provider, "total"), -1L).takeIf { prefs.contains(key(provider, "total")) },
                prefs.getLong(key(provider, "speed"), -1L).takeIf { prefs.contains(key(provider, "speed")) },
                prefs.getLong(key(provider, "remaining"), -1L).takeIf { prefs.contains(key(provider, "remaining")) },
                prefs.getBoolean(key(provider, "vpn"), false).takeIf { prefs.contains(key(provider, "vpn")) },
                prefs
                    .getString(key(provider, "warnings"), null)
                    ?.lines()
                    .orEmpty()
                    .filter(String::isNotBlank)
                    .map(DiagnosticTextRedactor::redact),
                prefs.getString(key(provider, "technical"), null)?.let(DiagnosticTextRedactor::redact),
            )
        }
    }

    private fun key(
        provider: DownloadProvider,
        field: String,
    ): String = "${provider.name}.$field"

    private const val MAX_WARNING_CHARS = 1_024
}
