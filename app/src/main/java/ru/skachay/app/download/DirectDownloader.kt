package ru.skachay.app.download

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
import okhttp3.OkHttpClient
import okhttp3.Request

class DirectDownloader(
    private val client: OkHttpClient = OkHttpClient(),
    private val retryDelay: (Long) -> Unit = { Thread.sleep(it) },
) {
    fun download(
        url: String,
        destination: File,
        connections: Int = 8,
        headers: Map<String, String> = emptyMap(),
        onProgress: (Long, Long?) -> Unit = { _, _ -> },
    ): File {
        require(connections in 1..16)
        val parent = requireNotNull(destination.absoluteFile.parentFile)
        require(parent.isDirectory)
        val temporary = File.createTempFile("download-", ".part", parent)
        try {
            val progress = Progress(onProgress)
            val probe = request(url, headers).header("Range", "bytes=0-0")
                .header("Accept-Encoding", "identity").build()
            val info = retry {
                client.newCall(probe).execute().use { response ->
                    if (!response.isSuccessful) throw HttpStatusException(response.code)
                    if (response.code == 206) {
                        val match = Regex("bytes 0-0/(\\d+)").matchEntire(response.header("Content-Range").orEmpty())
                        val size = match?.groupValues?.get(1)?.toLongOrNull()
                        val etag = response.header("ETag")?.takeUnless { it.startsWith("W/", true) }
                        size?.let { Probe(it, etag ?: response.header("Last-Modified"), etag) }
                    } else null
                }
            }
            if (info == null || info.size < 2 || connections == 1) {
                downloadWhole(url, temporary, headers, progress)
            } else {
                try {
                    downloadRanges(url, temporary, info, connections, headers, progress)
                } catch (error: IOException) {
                    if (Thread.currentThread().isInterrupted) throw error
                    downloadWhole(url, temporary, headers, progress)
                }
            }
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            return destination
        } finally {
            temporary.delete()
        }
    }

    private fun request(url: String, headers: Map<String, String>): Request.Builder =
        Request.Builder().url(url)
            .header("User-Agent", "Skachay/0.1 (Android; https://github.com/donatefrost-cell/skachay-android)")
            .apply { headers.forEach { (name, value) -> header(name, value) } }

    private fun downloadWhole(url: String, destination: File, headers: Map<String, String>, progress: Progress) {
        val request = request(url, headers).header("Accept-Encoding", "identity").build()
        retry {
            client.newCall(request).execute().use { response ->
                if (response.code != 200) throw HttpStatusException(response.code)
                val body = response.body
                val expected = body.contentLength()
                progress.reset(expected.takeIf { it >= 0 })
                destination.outputStream().use { copy(body.byteStream(), it, progress) }
                if (expected >= 0 && destination.length() != expected) throw IOException("Incomplete response")
            }
        }
    }

    private fun downloadRanges(url: String, destination: File, info: Probe, connections: Int, headers: Map<String, String>, progress: Progress) {
        val size = info.size
        val count = minOf(connections.toLong(), size).toInt()
        val parts = List(count) { File.createTempFile("part-", ".tmp", destination.parentFile) }
        val executor = Executors.newFixedThreadPool(count)
        try {
            progress.reset(size)
            val tasks = parts.mapIndexed { index, part ->
                val start = size * index / count
                val end = size * (index + 1) / count - 1
                Callable {
                    retry {
                        val completed = part.length()
                        if (completed > 0 && (info.validator == null || completed >= end - start + 1)) {
                            throw UnusableRangeException("Cannot safely resume this range")
                        }
                        val requestStart = start + completed
                        val request = request(url, headers).header("Range", "bytes=$requestStart-$end")
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
            executor.shutdownNow()
            parts.forEach(File::delete)
        }
    }

    private data class Probe(val size: Long, val validator: String?, val etag: String?)

    private class HttpStatusException(val code: Int) : IOException("HTTP $code")
    private class UnusableRangeException(message: String) : IOException(message)

    private fun <T> retry(operation: () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return operation()
            } catch (error: IOException) {
                val retryableStatus = error !is HttpStatusException || error.code == 408 || error.code == 429 || error.code in 500..599
                if (attempt >= 2 || error is UnusableRangeException || !retryableStatus || Thread.currentThread().isInterrupted) throw error
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

    private fun copy(input: InputStream, output: OutputStream, progress: Progress) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            output.write(buffer, 0, read)
            progress.advance(read)
        }
    }

    private class Progress(private val listener: (Long, Long?) -> Unit) {
        private var current = 0L
        private var reported = 0L
        private var total: Long? = null

        @Synchronized
        fun reset(size: Long?) {
            current = 0
            total = size
        }

        @Synchronized
        fun advance(bytes: Int) {
            current += bytes
            if (current >= reported) {
                reported = current
                listener(current, total)
            }
        }
    }
}
