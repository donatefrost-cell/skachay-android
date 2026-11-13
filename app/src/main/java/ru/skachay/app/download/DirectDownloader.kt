package ru.skachay.app.download

import android.annotation.SuppressLint
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class DirectDownloader(
    private val client: OkHttpClient = OkHttpClient(),
    private val retryDelay: (Long) -> Unit = { Thread.sleep(it) },
) {
    fun download(
        url: String,
        destination: File,
        connections: Int = 8,
        headers: Map<String, String> = emptyMap(),
        retries: Int = 2,
        onFirstByte: () -> Unit = {},
        onProgress: (Long, Long?) -> Unit = { _, _ -> },
    ): File {
        require(connections in 1..16)
        require(retries in 0..5)
        val parent = requireNotNull(destination.absoluteFile.parentFile)
        require(parent.isDirectory)
        val temporary = File.createTempFile("download-", ".part", parent)
        try {
            val progress = Progress(onProgress, onFirstByte)
            val probe =
                request(url, headers)
                    .header("Range", "bytes=0-0")
                    .header("Accept-Encoding", "identity")
                    .build()
            val info =
                retry(retries) {
                    client.newCall(probe).execute().use { response ->
                        if (response.code in setOf(400, 403, 405, 416, 501)) return@use null
                        if (!response.isSuccessful) throw HttpStatusException(response.code)
                        if (response.code == 206) {
                            val match = Regex("bytes 0-0/(\\d+)").matchEntire(response.header("Content-Range").orEmpty())
                            val size = match?.groupValues?.get(1)?.toLongOrNull()
                            val etag = response.header("ETag")?.takeUnless { it.startsWith("W/", true) }
                            size?.let { Probe(it, etag ?: response.header("Last-Modified"), etag) }
                        } else {
                            null
                        }
                    }
                }
            val canUseRanges =
                info != null && info.size >= 2 && connections > 1 &&
                    DownloadSpacePolicy.hasEnough(DownloadSpacePolicy.availableBytes(parent), info.size, copies = 2)
            if (info != null) ensureSpace(parent, info.size, copies = 1, existingBytesToReplace = 0)
            if (info == null || info.size < 2 || connections == 1 || !canUseRanges) {
                downloadWhole(url, temporary, headers, progress, retries)
            } else {
                try {
                    downloadRanges(url, temporary, info, connections, headers, progress, retries)
                } catch (error: IOException) {
                    if (Thread.currentThread().isInterrupted) throw error
                    downloadWhole(url, temporary, headers, progress, retries)
                }
            }
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            return destination
        } finally {
            temporary.delete()
        }
    }

    private fun request(
        url: String,
        headers: Map<String, String>,
    ): Request.Builder =
        Request
            .Builder()
            .url(url)
            .header("User-Agent", "Skachay/0.1 (Android; https://github.com/donatefrost-cell/skachay-android)")
            .apply { headers.forEach { (name, value) -> header(name, value) } }

    private fun downloadWhole(
        url: String,
        destination: File,
        headers: Map<String, String>,
        progress: Progress,
        retries: Int,
    ) {
        val request = request(url, headers).header("Accept-Encoding", "identity").build()
        retry(retries) {
            client.newCall(request).execute().use { response ->
                if (response.code != 200) throw HttpStatusException(response.code)
                val body = response.body
                val expected = body.contentLength()
                if (expected >= 0) ensureSpace(destination.parentFile, expected, copies = 1, existingBytesToReplace = destination.length())
                progress.reset(expected.takeIf { it >= 0 })
                destination.outputStream().use { copy(body.byteStream(), it, progress) }
                if (expected >= 0 && destination.length() != expected) throw IOException("Incomplete response")
            }
        }
    }

    private fun downloadRanges(
        url: String,
        destination: File,
        info: Probe,
        connections: Int,
        headers: Map<String, String>,
        progress: Progress,
        retries: Int,
    ) {
        val size = info.size
        val segments = rangeSegments(size, connections)
        val parts = mutableListOf<File>()
        var rangeExecutor: java.util.concurrent.ExecutorService? = null
        try {
            repeat(segments.size) { parts += File.createTempFile("part-", ".tmp", destination.parentFile) }
            val executor = Executors.newFixedThreadPool(minOf(connections, parts.size))
            rangeExecutor = executor
            progress.reset(size)
            val tasks =
                parts.mapIndexed { index, part ->
                    val start = segments[index].first
                    val end = segments[index].last
                    Callable {
                        retry(retries) {
                            val completed = part.length()
                            if (completed > 0 && (info.validator == null || completed >= end - start + 1)) {
                                throw UnusableRangeException("Cannot safely resume this range")
                            }
                            val requestStart = start + completed
                            val request =
                                request(url, headers)
                                    .header("Range", "bytes=$requestStart-$end")
                                    .header("Accept-Encoding", "identity")
                                    .apply { info.validator?.let { header("If-Range", it) } }
                                    .build()
                            client.newCall(request).execute().use { response ->
                                if (response.code >= 400) throw HttpStatusException(response.code)
                                val expectedRange = "bytes $requestStart-$end/$size"
                                if (response.code != 206 || response.header("Content-Range") != expectedRange) {
                                    throw UnusableRangeException("Invalid range response")
                                }
                                val receivedEtag = response.header("ETag")
                                if (info.etag != null && receivedEtag != null && info.etag != receivedEtag) {
                                    throw UnusableRangeException("Resource changed")
                                }
                                FileOutputStream(part, completed > 0).buffered().use { output ->
                                    copy(response.body.byteStream(), output, progress)
                                }
                                if (part.length() != end - start + 1) throw IOException("Incomplete range")
                            }
                        }
                    }
                }
            val results = executor.invokeAll(tasks)
            for (result in results) {
                try {
                    result.get()
                } catch (e: java.util.concurrent.ExecutionException) {
                    throw IOException("Range request failed", e.cause)
                }
            }
            destination.outputStream().use { output ->
                parts.forEach { it.inputStream().use { input -> input.copyTo(output) } }
            }
        } finally {
            rangeExecutor?.shutdownNow()
            parts.forEach(File::delete)
        }
    }

    private data class Probe(
        val size: Long,
        val validator: String?,
        val etag: String?,
    )

    private class HttpStatusException(
        val code: Int,
    ) : IOException("HTTP $code")

    private class UnusableRangeException(
        message: String,
    ) : IOException(message)

    private fun <T> retry(
        retries: Int,
        operation: () -> T,
    ): T {
        var attempt = 0
        while (true) {
            try {
                return operation()
            } catch (error: IOException) {
                val retryableStatus = error !is HttpStatusException || error.code == 408 || error.code == 429 || error.code in 500..599
                if (attempt >= retries || error is UnusableRangeException || !retryableStatus ||
                    Thread.currentThread().isInterrupted
                ) {
                    throw error
                }
                try {
                    retryDelay(250L shl attempt)
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw InterruptedIOException("Download interrupted").apply { initCause(interrupted) }
                }
                attempt++
            }
        }
    }

    private fun copy(
        input: InputStream,
        output: OutputStream,
        progress: Progress,
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            output.write(buffer, 0, read)
            progress.advance(read)
        }
    }

    private class Progress(
        private val listener: (Long, Long?) -> Unit,
        private val firstByte: () -> Unit,
    ) {
        private var current = 0L
        private var reported = 0L
        private var total: Long? = null
        private var sawFirstByte = false

        @Synchronized
        fun reset(size: Long?) {
            current = 0
            total = size
            sawFirstByte = false
        }

        @Synchronized
        fun advance(bytes: Int) {
            current += bytes
            if (!sawFirstByte) {
                sawFirstByte = true
                firstByte()
            }
            if (current >= reported) {
                reported = current
                listener(current, total)
            }
        }
    }
}

internal object DownloadSpacePolicy {
    // Use the currently available space; do not clear other apps' caches for a download.
    @SuppressLint("UsableSpace")
    fun availableBytes(directory: File): Long = directory.usableSpace

    fun hasEnough(
        availableBytes: Long,
        expectedBytes: Long,
        copies: Int,
        existingBytesToReplace: Long = 0,
    ): Boolean {
        if (availableBytes < 0 || expectedBytes < 0 || copies < 1 || copies > 2 || existingBytesToReplace < 0) return false
        if (expectedBytes > Long.MAX_VALUE / copies) return false
        val required = expectedBytes * copies
        val usable = availableBytes.coerceAtLeast(0)
        val capacity = if (Long.MAX_VALUE - usable < existingBytesToReplace) Long.MAX_VALUE else usable + existingBytesToReplace
        return required <= capacity
    }
}

private fun ensureSpace(
    directory: File?,
    expectedBytes: Long,
    copies: Int,
    existingBytesToReplace: Long,
) {
    val available = directory?.let(DownloadSpacePolicy::availableBytes) ?: 0L
    if (!DownloadSpacePolicy.hasEnough(available, expectedBytes, copies, existingBytesToReplace)) {
        throw IOException("Not enough free storage for download")
    }
}

internal fun rangeSegments(
    size: Long,
    connections: Int,
    targetChunkBytes: Long = 8L * 1024 * 1024,
): List<LongRange> {
    require(size > 0)
    require(connections in 1..16)
    require(targetChunkBytes in 4L * 1024 * 1024..10L * 1024 * 1024)
    val count =
        if (size <= targetChunkBytes) {
            minOf(size, connections.toLong()).toInt()
        } else {
            (((size - 1) / targetChunkBytes + 1).coerceAtMost(MAX_RANGE_PARTS.toLong())).toInt()
        }
    val quotient = size / count
    val remainder = size % count

    fun boundary(index: Int): Long = quotient * index + remainder * index / count
    return List(count) { index ->
        val first = boundary(index)
        val exclusiveEnd = boundary(index + 1)
        first until exclusiveEnd
    }
}

private const val MAX_RANGE_PARTS = 4_096
