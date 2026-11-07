package ru.skachay.app.download

import android.content.Context
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import ru.skachay.app.diagnostics.DownloadStage

internal object YouTubeDirectTransfer {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun download(
        context: Context,
        streams: YouTubeStreams,
        directory: File,
        connections: Int,
        retries: Int,
        onStage: (DownloadStage) -> Unit,
        onProgress: (Long, Long?) -> Unit,
        onFirstByte: () -> Unit,
    ): File {
        require(streams.video != null || streams.audio != null)
        val parts = listOfNotNull(streams.video, streams.audio)
        if (parts.any { it.directUrl == null }) throw IOException("Direct media stream unavailable")
        if (parts.any { it.extension !in setOf("mp4", "m4a") }) throw IOException("Unsupported direct media container")

        val expectedTotal = parts.map { it.sizeBytes }.takeIf { sizes -> sizes.all { it != null } }
            ?.sumOf { requireNotNull(it) }
        var completedBytes = 0L
        val files = parts.mapIndexed { index, format ->
            val file = File(directory, "youtube-$index.${format.extension}")
            onStage(DownloadStage.TRANSFER)
            DirectDownloader(client).download(
                url = requireNotNull(format.directUrl),
                destination = file,
                connections = connections,
                headers = format.httpHeaders,
                retries = retries,
                onFirstByte = onFirstByte,
                onProgress = { received, total ->
                    val combinedTotal = expectedTotal ?: total?.let { completedBytes + it }
                    onProgress(completedBytes + received, combinedTotal)
                },
            )
            completedBytes += file.length()
            file
        }

        if (streams.video == null) return files.single()
        if (streams.audio == null) return files.single()

        onStage(DownloadStage.MERGE)
        val output = File(directory, "video.mp4")
        merge(context, files[0], files[1], output)
        files.forEach(File::delete)
        return output
    }

    private fun merge(context: Context, video: File, audio: File, output: File) {
        val executable = File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so")
        if (!executable.isFile || !executable.canExecute()) throw IOException("FFmpeg is unavailable")
        val process = try {
            ProcessBuilder(
                executable.absolutePath,
                "-nostdin", "-hide_banner", "-loglevel", "error", "-y",
                "-i", video.absolutePath,
                "-i", audio.absolutePath,
                "-map", "0:v:0", "-map", "1:a:0",
                "-c", "copy", "-movflags", "+faststart",
                output.absolutePath,
            ).redirectErrorStream(true).start()
        } catch (error: IOException) {
            throw IOException("Could not start FFmpeg", error)
        }
        val tail = ArrayDeque<String>()
        try {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    tail.addLast(line.take(300))
                    while (tail.size > 8) tail.removeFirst()
                }
            }
            if (process.waitFor() != 0 || !output.isFile || output.length() == 0L) {
                throw IOException("FFmpeg merge failed: ${tail.joinToString(" ").take(1_000)}")
            }
        } catch (interrupted: InterruptedException) {
            process.destroyForcibly()
            Thread.currentThread().interrupt()
            throw IOException("FFmpeg merge interrupted", interrupted)
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }
}
