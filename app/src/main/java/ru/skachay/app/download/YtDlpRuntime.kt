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
import ru.skachay.app.settings.Quality
import ru.skachay.app.diagnostics.DownloadStage
import ru.skachay.app.diagnostics.stageFromYtDlpOutput
import ru.skachay.app.choice.AvailableFormat

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
        if (System.currentTimeMillis() - prefs.getLong("last_update_attempt", 0) >= DAY_MS) update(context)
    }

    private fun update(context: Context) {
        initialize(context)
        runtimeLock.write {
            context.getSharedPreferences("engine", Context.MODE_PRIVATE).edit()
                .putLong("last_update_attempt", System.currentTimeMillis()).apply()
            YoutubeDL.updateYoutubeDL(context.applicationContext, YoutubeDL.UpdateChannel.STABLE)
        }
    }

    fun extractPreview(context: Context, url: String): String {
        initialize(context)
        return RetryAfterUpdate.run(
            operation = {
                runtimeLock.read {
                    val request = com.yausername.youtubedl_android.YoutubeDLRequest(url)
                        .addOption("--no-playlist").addOption("--dump-single-json")
                    YoutubeDL.execute(request).out
                }
            },
            update = { update(context) },
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
    ): File {
        initialize(context)
        val template = File(directory, "video.%(ext)s").absolutePath
        RetryAfterUpdate.run(
            operation = {
                runtimeLock.read {
                    onStage(DownloadStage.EXTRACTION)
                    val request = YtDlpOptions.request(url, template, quality, infoJson?.absolutePath, selected, videoOnly)
                    YoutubeDL.execute(request, UUID.randomUUID().toString()) { progress, _, line ->
                        stageFromYtDlpOutput(line.orEmpty())?.let(onStage)
                        onProgress(progress.takeIf { it in 0f..100f }?.toInt())
                    }
                }
            },
            update = { update(context) },
        )
        return directory.listFiles()?.firstOrNull {
            it.isFile && it.name.startsWith("video.") && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl")
        } ?: throw IOException("No media file produced")
    }
}
