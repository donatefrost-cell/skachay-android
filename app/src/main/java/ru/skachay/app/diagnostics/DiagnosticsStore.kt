package ru.skachay.app.diagnostics

import android.content.Context
import java.util.UUID

internal object DiagnosticsStore {
    const val PREFERENCES = "diagnostics"

    @Synchronized
    fun begin(context: Context, provider: DownloadProvider): String {
        val job = UUID.randomUUID().toString()
        val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(key(provider, "job"), job)
            putString(key(provider, "status"), DiagnosticStatus.RUNNING.name)
            putLong(key(provider, "timestamp"), System.currentTimeMillis())
            DownloadStage.entries.forEach { remove(key(provider, it.name)) }
            listOf("bytes", "total", "speed", "remaining", "vpn", "warnings").forEach { remove(key(provider, it)) }
        }.apply()
        return job
    }

    @Synchronized
    fun clearEngineWarnings(context: Context, provider: DownloadProvider) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
            .remove(key(provider, "warnings"))
            .apply()
    }

    @Synchronized
    fun recordEngineWarnings(context: Context, provider: DownloadProvider, warnings: List<String>) {
        if (warnings.isEmpty()) return
        val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val existing = prefs.getString(key(provider, "warnings"), null).orEmpty()
            .lines().filter(String::isNotBlank)
        val combined = (existing + warnings).distinctBy { it.lowercase() }.takeLast(24)
        prefs.edit().putString(key(provider, "warnings"), combined.joinToString("\n")).apply()
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
        prefs.edit().apply {
            putString(key(provider, "status"), status.name)
            if (error != null) putString(key(provider, "error"), error)
            durations.forEach { (stage, millis) -> putLong(key(provider, stage.name), millis) }
            transfer?.let {
                putLong(key(provider, "bytes"), it.bytesReceived)
                it.totalBytes?.let { total -> putLong(key(provider, "total"), total) }
                it.averageBytesPerSecond?.let { speed -> putLong(key(provider, "speed"), speed) }
                it.remainingMillis?.let { remaining -> putLong(key(provider, "remaining"), remaining) }
                it.firstByteMillis?.let { firstByte -> putLong(key(provider, DownloadStage.FIRST_BYTE.name), firstByte) }
            }
            vpnActive?.let { putBoolean(key(provider, "vpn"), it) }
        }.apply()
    }

    @Synchronized
    fun markInterrupted(context: Context) {
        val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        DownloadProvider.entries.forEach { provider ->
            if (prefs.getString(key(provider, "status"), null) == DiagnosticStatus.RUNNING.name) {
                editor.putString(key(provider, "status"), DiagnosticStatus.INTERRUPTED.name)
                editor.putString(key(provider, "error"), "Загрузка прервалась. Попробуйте ещё раз.")
            }
        }
        editor.apply()
    }

    fun read(context: Context): List<DiagnosticEntry> {
        val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        return DownloadProvider.entries.map { provider ->
            val status = DiagnosticStatus.entries.firstOrNull { it.name == prefs.getString(key(provider, "status"), null) }
                ?: DiagnosticStatus.NEVER
            val durations = DownloadStage.entries.mapNotNull { stage ->
                if (prefs.contains(key(provider, stage.name))) stage to prefs.getLong(key(provider, stage.name), 0) else null
            }.toMap()
            DiagnosticEntry(
                provider, status, prefs.getLong(key(provider, "timestamp"), 0),
                prefs.getString(key(provider, "error"), null), durations,
                prefs.getLong(key(provider, "bytes"), -1L).takeIf { prefs.contains(key(provider, "bytes")) },
                prefs.getLong(key(provider, "total"), -1L).takeIf { prefs.contains(key(provider, "total")) },
                prefs.getLong(key(provider, "speed"), -1L).takeIf { prefs.contains(key(provider, "speed")) },
                prefs.getLong(key(provider, "remaining"), -1L).takeIf { prefs.contains(key(provider, "remaining")) },
                prefs.getBoolean(key(provider, "vpn"), false).takeIf { prefs.contains(key(provider, "vpn")) },
                prefs.getString(key(provider, "warnings"), null)?.lines().orEmpty().filter(String::isNotBlank),
            )
        }
    }

    private fun key(provider: DownloadProvider, field: String): String = "${provider.name}.$field"
}
