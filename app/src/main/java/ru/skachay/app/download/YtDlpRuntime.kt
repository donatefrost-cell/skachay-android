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
import ru.skachay.app.settings.SettingsRepository
import ru.skachay.app.diagnostics.DownloadStage
import ru.skachay.app.diagnostics.stageFromYtDlpOutput
import ru.skachay.app.choice.AvailableFormat

internal enum class EngineUpdateResult { UPDATED, ALREADY_CURRENT }

internal object YtDlpRuntime {
    private val initialization = Any()
    private val runtimeLock = ReentrantReadWriteLock()
    @Volatile private var initialized = false
    private const val DAY_MS = 24 * 60 * 60 * 1000L

    fun initialize(context: Context) {
        synchronized(initialization) {
            if (initialized) return
            YoutubeDL.init(context.applicationContext)
            FFmpeg.init(context.applicationContext)
            initialized = true
        }
    }

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

    fun extractPreview(context: Context, url: String): String {
        initialize(context)
        val retries = runBlocking { SettingsRepository.forContext(context).settings.first().retries }
        return RetryAfterUpdate.run(
            operation = {
                runtimeLock.read {
                    val request = com.yausername.youtubedl_android.YoutubeDLRequest(url)
                        .addOption("--no-playlist").addOption("--dump-single-json")
                    YtDlpOptions.addRetries(request, retries)
                    YoutubeDL.execute(request).out
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
    ): File {
        initialize(context)
        val template = File(directory, "video.%(ext)s").absolutePath
        RetryAfterUpdate.run(
            operation = {
                runtimeLock.read {
                    onStage(DownloadStage.EXTRACTION)
                    val request = YtDlpOptions.request(
                        url, template, quality, infoJson?.absolutePath, selected, videoOnly, retries,
                        audioExport, audioBitrate, subtitleLanguage, subtitleAutomatic, embedSubtitles,
                    )
                    YoutubeDL.execute(request, UUID.randomUUID().toString()) { progress, _, line ->
                        stageFromYtDlpOutput(line.orEmpty())?.let(onStage)
                        onProgress(progress.takeIf { it in 0f..100f }?.toInt())
                    }
                }
            },
            update = { update(context, selectedChannel(context)) },
        )
        return directory.listFiles()?.firstOrNull {
            it.isFile && it.name.startsWith("video.") && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") &&
                !it.name.endsWith(".srt", ignoreCase = true) && !it.name.endsWith(".vtt", ignoreCase = true)
        } ?: throw IOException("No media file produced")
    }
}
