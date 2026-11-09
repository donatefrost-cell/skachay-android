package ru.skachay.app.download

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import ru.skachay.app.settings.EngineChannel
import ru.skachay.app.settings.Quality
import ru.skachay.app.settings.VideoBitrateLimit
import ru.skachay.app.settings.SettingsRepository
import ru.skachay.app.diagnostics.DownloadStage
import ru.skachay.app.diagnostics.stageFromYtDlpOutput
import ru.skachay.app.diagnostics.YtDlpProgress
import ru.skachay.app.diagnostics.YtDlpProgressParser
import ru.skachay.app.diagnostics.YtDlpWarningParser
import ru.skachay.app.diagnostics.DiagnosticsStore
import ru.skachay.app.diagnostics.providerFor
import ru.skachay.app.choice.AvailableFormat

internal enum class EngineUpdateResult { UPDATED, ALREADY_CURRENT }

internal object YtDlpRuntime {
    private val initialization = Any()
    private val runtimeLock = ReentrantReadWriteLock()
    @Volatile private var initialized = false
    @Volatile private var initializationDurationMs: Long? = null
    private const val DAY_MS = 24 * 60 * 60 * 1000L

    fun initialize(context: Context) {
        synchronized(initialization) {
            if (initialized) return
            val started = android.os.SystemClock.elapsedRealtime()
            YoutubeDL.init(context.applicationContext)
            FFmpeg.init(context.applicationContext)
            initializationDurationMs = (android.os.SystemClock.elapsedRealtime() - started).coerceAtLeast(0)
            initialized = true
        }
    }

    fun startupDurationMs(): Long? = initializationDurationMs

    fun updateIfDue(context: Context) {
        val prefs = context.getSharedPreferences("engine", Context.MODE_PRIVATE)
        if (System.currentTimeMillis() - prefs.getLong("last_update_attempt", 0) >= DAY_MS) {
            update(context, selectedChannel(context), onlyIfDue = true)
        }
    }

    fun updateNow(context: Context, channel: EngineChannel): EngineUpdateResult = when (update(context, channel)) {
        YoutubeDL.UpdateStatus.DONE -> EngineUpdateResult.UPDATED
        YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> EngineUpdateResult.ALREADY_CURRENT
        null -> error("Manual update was unexpectedly skipped")
    }

    private fun selectedChannel(context: Context): EngineChannel = runBlocking {
        SettingsRepository.forContext(context).settings.first().engineChannel
    }

    private fun update(context: Context, channel: EngineChannel, onlyIfDue: Boolean = false): YoutubeDL.UpdateStatus? {
        initialize(context)
        return runtimeLock.write {
            val prefs = context.getSharedPreferences("engine", Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            if (onlyIfDue && now - prefs.getLong("last_update_attempt", 0) < DAY_MS) return@write null
            prefs.edit().putLong("last_update_attempt", now).apply()
            val source = when (channel) {
                EngineChannel.STABLE -> YoutubeDL.UpdateChannel.STABLE
                EngineChannel.NIGHTLY -> YoutubeDL.UpdateChannel.NIGHTLY
            }
            YoutubeDL.updateYoutubeDL(context.applicationContext, source)
        }
    }

    fun extractPreview(
        context: Context,
        url: String,
        onWarnings: (List<String>) -> Unit = { DiagnosticsStore.recordEngineWarnings(context, providerFor(url), it) },
    ): String {
        initialize(context)
        val retries = runBlocking { SettingsRepository.forContext(context).settings.first().retries }
        return RetryAfterUpdate.run(
            operation = {
                runtimeLock.read {
                    val request = com.yausername.youtubedl_android.YoutubeDLRequest(url)
                        .addOption("--no-playlist").addOption("--skip-download").addOption("--dump-single-json")
                    YtDlpOptions.addRetries(request, retries)
                    addRuntimeSupport(context, url, request)
                    executeWithWarningCapture(request, onWarnings = onWarnings).out
                }
            },
            update = { update(context, selectedChannel(context)) },
        )
    }

    fun download(
        context: Context,
        url: String,
        directory: File,
        quality: Quality = Quality.AUTO,
        onProgress: (Int?) -> Unit,
        onStage: (DownloadStage) -> Unit = {},
        infoJson: File? = null,
        selected: AvailableFormat? = null,
        videoOnly: Boolean = false,
        retries: Int = 2,
        audioExport: AudioExport = AudioExport.SOURCE,
        audioBitrate: AudioBitrate = AudioBitrate.K192,
        subtitleLanguage: String? = null,
        subtitleAutomatic: Boolean = false,
        embedSubtitles: Boolean = false,
        onDetailedProgress: (YtDlpProgress) -> Unit = {},
        videoBitrateLimit: VideoBitrateLimit = VideoBitrateLimit.ANY,
        embedThumbnail: Boolean = false,
        onWarnings: (List<String>) -> Unit = { DiagnosticsStore.recordEngineWarnings(context, providerFor(url), it) },
    ): File {
        initialize(context)
        val template = File(directory, "video.%(ext)s").absolutePath
        RetryAfterUpdate.run(
            operation = {
                runtimeLock.read {
                    onStage(DownloadStage.EXTRACTION)
                    val request = YtDlpOptions.request(
                        url, template, quality, infoJson?.absolutePath, selected, videoOnly, retries,
                        audioExport, audioBitrate, subtitleLanguage, subtitleAutomatic, embedSubtitles, videoBitrateLimit, embedThumbnail,
                    )
                    addRuntimeSupport(context, url, request)
                    executeWithWarningCapture(request, UUID.randomUUID().toString(), { progress, _, line ->
                        stageFromYtDlpOutput(line)?.let(onStage)
                        val detailed = YtDlpProgressParser.parse(line)
                        if (detailed != null) onDetailedProgress(detailed)
                        else onProgress(progress.takeIf { it in 0f..100f }?.toInt())
                    }, onWarnings)
                }
            },
            update = { update(context, selectedChannel(context)) },
        )
        return directory.listFiles()?.firstOrNull {
            it.isFile && it.name.startsWith("video.") && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") &&
                !it.name.endsWith(".srt", ignoreCase = true) && !it.name.endsWith(".vtt", ignoreCase = true)
        } ?: throw IOException("No media file produced")
    }

    fun downloadPlaylist(
        context: Context,
        url: String,
        directory: File,
        quality: Quality,
        onProgress: (Int?) -> Unit,
        onStage: (DownloadStage) -> Unit = {},
        retries: Int = 2,
        audioExport: AudioExport = AudioExport.SOURCE,
        audioBitrate: AudioBitrate = AudioBitrate.K128,
        audioOnly: Boolean = false,
        subtitleLanguage: String? = null,
        subtitleAutomatic: Boolean = false,
        embedSubtitles: Boolean = false,
        onDetailedProgress: (YtDlpProgress) -> Unit = {},
        videoBitrateLimit: VideoBitrateLimit = VideoBitrateLimit.ANY,
        embedThumbnail: Boolean = false,
        onWarnings: (List<String>) -> Unit = { DiagnosticsStore.recordEngineWarnings(context, providerFor(url), it) },
    ): List<File> {
        require(YouTubePlaylistLink.isPurePlaylist(url))
        initialize(context)
        val template = File(directory, "%(playlist_index)03d - %(title).100B [%(id)s].%(ext)s").absolutePath
        RetryAfterUpdate.run(
            operation = {
                runtimeLock.read {
                    onStage(DownloadStage.EXTRACTION)
                    val request = YtDlpOptions.request(
                        url = url,
                        output = template,
                        quality = quality,
                        retries = retries,
                        audioExport = audioExport,
                        audioBitrate = audioBitrate,
                        subtitleLanguage = subtitleLanguage,
                        subtitleAutomatic = subtitleAutomatic,
                        embedSubtitles = embedSubtitles,
                        videoBitrateLimit = videoBitrateLimit,
                        embedThumbnail = embedThumbnail,
                        allowPlaylist = true,
                        playlistAudioOnly = audioOnly,
                    )
                    addRuntimeSupport(context, url, request)
                    executeWithWarningCapture(request, UUID.randomUUID().toString(), { progress, _, line ->
                        stageFromYtDlpOutput(line)?.let(onStage)
                        val detailed = YtDlpProgressParser.parse(line)
                        if (detailed != null) onDetailedProgress(detailed)
                        else onProgress(progress.takeIf { it in 0f..100f }?.toInt())
                    }, onWarnings)
                }
            },
            update = { update(context, selectedChannel(context)) },
        )
        val files = directory.listFiles().orEmpty().filter { file ->
            file.isFile && !file.name.endsWith(".part", true) && !file.name.endsWith(".ytdl", true) &&
                file.extension.lowercase() !in setOf("srt", "vtt", "json", "jpg", "jpeg", "png", "webp", "description")
        }.sortedBy { it.name }
        if (files.isEmpty()) throw IOException("No playlist media files produced")
        return files
    }

    private fun addRuntimeSupport(
        context: Context,
        url: String,
        request: com.yausername.youtubedl_android.YoutubeDLRequest,
    ) {
        YtDlpOptions.addRuntimeSupport(request, url, File(context.noBackupFilesDir, "yt-dlp-cache").absolutePath)
    }

    private fun executeWithWarningCapture(
        request: com.yausername.youtubedl_android.YoutubeDLRequest,
        processId: String? = null,
        callback: ((Float, Long, String) -> Unit)? = null,
        onWarnings: (List<String>) -> Unit,
    ): com.yausername.youtubedl_android.YoutubeDLResponse = try {
        YoutubeDL.execute(request, processId, callback).also { response ->
            YtDlpWarningParser.parse(response.err).takeIf { it.isNotEmpty() }?.let(onWarnings)
        }
    } catch (error: Exception) {
        YtDlpWarningParser.parse(error.message.orEmpty()).takeIf { it.isNotEmpty() }?.let(onWarnings)
        throw error
    }
}
