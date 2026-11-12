package ru.skachay.app.choice

import android.content.Context
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import android.os.SystemClock
import ru.skachay.app.download.YtDlpRuntime
import ru.skachay.app.diagnostics.DiagnosticsStore
import ru.skachay.app.diagnostics.DiagnosticTextRedactor
import ru.skachay.app.diagnostics.providerFor

internal data class CachedPreview(val file: File, val preview: VideoPreview)

internal object PreviewLifetime {
    private const val FRESH_MS = 15 * 60 * 1000L
    private const val RETAIN_MS = 60 * 60 * 1000L

    fun isFresh(age: Long): Boolean = age in 0..FRESH_MS
    fun isUsableForDownload(age: Long): Boolean = age in 0..RETAIN_MS
    fun isExpired(age: Long): Boolean = age > RETAIN_MS
}

internal object PreviewStore {
    private val lock = Any()
    private val prefetcher = Executors.newSingleThreadExecutor()
    private val extractionTimes = ConcurrentHashMap<String, Long>()
    private const val MAX_CACHE_BYTES = 5L * 1024 * 1024

    fun getOrFetch(context: Context, url: String): CachedPreview = synchronized(lock) {
        load(context, url)?.let { return it }
        val directory = directory(context)
        directory.listFiles()?.filter { PreviewLifetime.isExpired(System.currentTimeMillis() - it.lastModified()) }?.forEach(File::delete)
        val started = SystemClock.elapsedRealtime()
        val warnings = mutableListOf<String>()
        DiagnosticsStore.clearEngineWarnings(context, providerFor(url))
        val json = try {
            YtDlpRuntime.extractPreview(context, url) { captured ->
                warnings += captured
                DiagnosticsStore.recordEngineWarnings(context, providerFor(url), captured)
            }
        } finally {
            saveWarnings(context, url, warnings)
        }
        extractionTimes[key(url)] = (SystemClock.elapsedRealtime() - started).coerceAtLeast(0)
        val preview = PreviewParser.parse(json) ?: throw IOException("Invalid media metadata")
        val file = file(context, url)
        val temporary = File.createTempFile("preview-", ".tmp", directory)
        try {
            temporary.writeText(json, Charsets.UTF_8)
            if (temporary.length() > MAX_CACHE_BYTES) throw IOException("Media metadata is too large")
            java.nio.file.Files.move(temporary.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temporary.delete()
        }
        CachedPreview(file, preview)
    }

    fun prefetch(context: Context, url: String) {
        val appContext = context.applicationContext
        prefetcher.execute {
            try { getOrFetch(appContext, url) } catch (_: Exception) { }
        }
    }

    fun extractionDuration(url: String): Long? = extractionTimes[key(url)]

    fun load(context: Context, url: String, forDownload: Boolean = false): CachedPreview? {
        val file = file(context, url)
        val age = System.currentTimeMillis() - file.lastModified()
        val usable = if (forDownload) PreviewLifetime.isUsableForDownload(age) else PreviewLifetime.isFresh(age)
        if (!file.isFile || file.length() > MAX_CACHE_BYTES || !usable) return null
        return try {
            PreviewParser.parse(file.readText(Charsets.UTF_8))?.let { preview ->
                readWarnings(context, url).takeIf { it.isNotEmpty() }?.let {
                    DiagnosticsStore.recordEngineWarnings(context, providerFor(url), it)
                }
                CachedPreview(file, preview)
            }
        } catch (_: IOException) {
            null
        }
    }

    private fun saveWarnings(context: Context, url: String, warnings: List<String>) {
        val file = warningsFile(context, url)
        if (warnings.isEmpty()) {
            file.delete()
            return
        }
        runCatching {
            file.writeText(
                warnings.map { DiagnosticTextRedactor.redact(it).take(1_024) }
                    .distinctBy { it.lowercase() }.takeLast(24).joinToString("\n"),
                Charsets.UTF_8,
            )
        }
    }

    private fun readWarnings(context: Context, url: String): List<String> = try {
        warningsFile(context, url).takeIf { it.isFile }?.readLines(Charsets.UTF_8).orEmpty()
            .filter(String::isNotBlank).takeLast(24)
    } catch (_: IOException) {
        emptyList()
    }

    private fun directory(context: Context): File = File(context.cacheDir, "previews").apply {
        if (!isDirectory && !mkdirs()) throw IOException("Could not create preview folder")
    }

    private fun file(context: Context, url: String): File {
        val name = key(url)
        return File(directory(context), "$name.json")
    }

    private fun warningsFile(context: Context, url: String): File = File(directory(context), "${key(url)}.warnings")

    private fun key(url: String): String = MessageDigest.getInstance("SHA-256")
        .digest(url.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
