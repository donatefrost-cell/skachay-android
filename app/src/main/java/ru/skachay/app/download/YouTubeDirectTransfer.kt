package ru.skachay.app.download

import android.content.Context
import ru.skachay.app.diagnostics.DownloadStage
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

internal object YouTubeDirectTransfer {
    fun <T> withFallback(
        direct: () -> T,
        onDirectFailure: (IOException) -> Unit,
        fallback: () -> T,
    ): T =
        try {
            direct()
        } catch (error: IOException) {
            if (Thread.currentThread().isInterrupted) throw error
            if (error is IncompleteRangeTransferException) throw error
            onDirectFailure(error)
            try {
                fallback()
            } catch (fallbackError: Exception) {
                fallbackError.addSuppressed(error)
                throw fallbackError
            }
        }

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

        val expectedTotal =
            parts
                .map { it.sizeBytes }
                .takeIf { sizes -> sizes.all { it != null } }
                ?.sumOf { requireNotNull(it) }
        var completedBytes = 0L
        val files =
            parts.mapIndexed { index, format ->
                val file = File(directory, "youtube-$index.${format.extension}")
                onStage(DownloadStage.TRANSFER)
                val mediaDownloader =
                    MediaDownloadClient.forSource("https://youtube.com")
                        ?: throw IOException("Unsupported YouTube media source")
                if (!ResolvedMediaNetworkPolicy.isAllowed("https://youtube.com", requireNotNull(format.directUrl))) {
                    throw IOException("Unsafe YouTube media URL")
                }
                mediaDownloader.download(
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

    internal fun merge(
        context: Context,
        video: File,
        audio: File,
        output: File,
    ) {
        val executable = File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so")
        if (!executable.isFile || !executable.canExecute()) throw IOException("FFmpeg is unavailable")
        val process =
            try {
                ProcessBuilder(
                    executable.absolutePath,
                    "-nostdin",
                    "-hide_banner",
                    "-loglevel",
                    "error",
                    "-y",
                    "-i",
                    video.absolutePath,
                    "-i",
                    audio.absolutePath,
                    "-map",
                    "0:v:0",
                    "-map",
                    "1:a:0",
                    "-c",
                    "copy",
                    "-movflags",
                    "+faststart",
                    output.absolutePath,
                ).redirectErrorStream(true)
                    .apply {
                        environment()["LD_LIBRARY_PATH"] = FfmpegRuntime.libraryPath(context)
                    }.start()
            } catch (error: IOException) {
                throw IOException("Could not start FFmpeg", error)
            }
        val outputDrain =
            Thread({
                try {
                    process.inputStream.use { input ->
                        val buffer = ByteArray(8_192)
                        while (input.read(buffer) != -1) Unit
                    }
                } catch (_: IOException) {
                    // The process may close its output stream as it exits.
                }
            }, "skachay-ffmpeg-output").apply {
                isDaemon = true
                start()
            }
        try {
            if (!process.waitFor(2, TimeUnit.MINUTES)) {
                process.destroyForcibly()
                throw IOException("FFmpeg merge timed out")
            }
            if (process.waitFor() != 0 || !output.isFile || output.length() == 0L) {
                throw IOException("FFmpeg merge failed")
            }
        } catch (interrupted: InterruptedException) {
            process.destroyForcibly()
            Thread.currentThread().interrupt()
            throw IOException("FFmpeg merge interrupted", interrupted)
        } finally {
            if (process.isAlive) process.destroyForcibly()
            outputDrain.interrupt()
        }
    }
}
