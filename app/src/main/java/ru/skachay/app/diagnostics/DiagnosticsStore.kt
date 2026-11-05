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
        }.apply()
        return job
    }

    @Synchronized
    fun finish(
        context: Context,
        provider: DownloadProvider,
        job: String,
        status: DiagnosticStatus,
        durations: Map<DownloadStage, Long>,
        error: String? = null,
    ) {
        val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (prefs.getString(key(provider, "job"), null) != job) return
        prefs.edit().apply {
            putString(key(provider, "status"), status.name)
            if (error != null) putString(key(provider, "error"), error)
            durations.forEach { (stage, millis) -> putLong(key(provider, stage.name), millis) }
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
            DiagnosticEntry(provider, status, prefs.getLong(key(provider, "timestamp"), 0),
                prefs.getString(key(provider, "error"), null), durations)
        }
    }

    private fun key(provider: DownloadProvider, field: String): String = "${provider.name}.$field"
}
