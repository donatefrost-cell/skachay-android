package ru.skachay.app.download

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.OkHttpClient
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DirectDownloaderTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun splitsLargeFilesIntoBoundedRangeChunks() {
        val size = 100L * 1024 * 1024 + 3
        val segments = rangeSegments(size, connections = 8)
        assertTrue(segments.size > 8)
        assertEquals(0L, segments.first().first)
        assertEquals(size - 1, segments.last().last)
        assertTrue(segments.all { it.last - it.first + 1 <= 8L * 1024 * 1024 })
        assertTrue(segments.zipWithNext().all { (left, right) -> left.last + 1 == right.first })
    }

    @Test
    fun capsRangeMetadataForVeryLargeFilesWithoutOverflowingOffsets() {
        val segments = rangeSegments(Long.MAX_VALUE, connections = 16)
        assertEquals(4_096, segments.size)
        assertEquals(0L, segments.first().first)
        assertEquals(Long.MAX_VALUE - 1, segments.last().last)
        assertTrue(segments.zipWithNext().all { (left, right) -> left.last + 1 == right.first })
    }

    @Test
    fun checksAvailableSpaceForDownloadAndRangeParts() {
        assertTrue(DownloadSpacePolicy.hasEnough(100, 80, copies = 1))
        assertTrue(DownloadSpacePolicy.hasEnough(100, 60, copies = 2))
        assertTrue(DownloadSpacePolicy.hasEnough(40, 60, copies = 1, existingBytesToReplace = 20))
        assertTrue(!DownloadSpacePolicy.hasEnough(100, 60, copies = 2))
        assertTrue(!DownloadSpacePolicy.hasEnough(100, Long.MAX_VALUE, copies = 2))
    }

    @Test
    fun retriesTemporaryServerFailuresWithBackoff() {
        val attempts = AtomicInteger()
        val waits = mutableListOf<Long>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/file") { exchange ->
            if (attempts.incrementAndGet() <= 2) {
                exchange.sendResponseHeaders(503, -1)
            } else {
                val content = byteArrayOf(1, 2, 3)
                exchange.sendResponseHeaders(200, content.size.toLong())
                exchange.responseBody.use { it.write(content) }
            }
            exchange.close()
        }
        server.start()
        try {
            val output = temp.newFile("retry.bin")
            DirectDownloader(OkHttpClient(), retryDelay = { waits.add(it) })
                .download("http://127.0.0.1:${server.address.port}/file", output)
            assertArrayEquals(byteArrayOf(1, 2, 3), output.readBytes())
            assertEquals(listOf(250L, 500L), waits)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun resumesAnInterruptedRangeWhenTheResourceHasNotChanged() {
        val content = ByteArray(2048) { (it % 251).toByte() }
        val interrupted = AtomicBoolean()
        val resumed = AtomicBoolean()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val executor = Executors.newCachedThreadPool()
        server.executor = executor
        server.createContext("/file") { exchange ->
            val range = exchange.requestHeaders.getFirst("Range")
            val bounds = Regex("bytes=(\\d+)-(\\d+)").matchEntire(range.orEmpty())
            if (bounds == null) {
                exchange.sendResponseHeaders(200, content.size.toLong())
                exchange.responseBody.use { it.write(content) }
            } else {
                val start = bounds.groupValues[1].toInt()
                val end = bounds.groupValues[2].toInt()
                if (start == 512 && exchange.requestHeaders.getFirst("If-Range") == "\"fixed\"") resumed.set(true)
                exchange.responseHeaders.add("ETag", "\"fixed\"")
                exchange.responseHeaders.add("Content-Range", "bytes $start-$end/${content.size}")
                exchange.sendResponseHeaders(206, (end - start + 1).toLong())
                if (start == 0 && end == 1023 && interrupted.compareAndSet(false, true)) {
                    exchange.responseBody.write(content, 0, 512)
                    exchange.close()
                } else {
                    exchange.responseBody.use { it.write(content, start, end - start + 1) }
                }
            }
        }
        server.start()
        try {
            val output = temp.newFile("resume.bin")
            DirectDownloader(OkHttpClient(), retryDelay = { })
                .download("http://127.0.0.1:${server.address.port}/file", output, connections = 2)
            assertArrayEquals(content, output.readBytes())
            assertTrue("Expected a request starting at the interrupted byte", resumed.get())
        } finally {
            server.stop(0)
            executor.shutdownNow()
        }
    }

    @Test
    fun downloadsPublicImageWhenLiveNetworkTestingIsEnabled() {
        assumeTrue(System.getenv("SKACHAY_LIVE_TEST") == "1")
        val output = temp.newFile("public.jpg")
        DirectDownloader().download("https://upload.wikimedia.org/wikipedia/commons/a/a9/Example.jpg", output)
        assertEquals("image/jpeg", MediaType.detect(output.inputStream().use { it.readNBytes(32) }, ""))
        assertTrue(output.length() > 1_000)
    }

    @Test
    fun downloadsInParallelWhenServerSupportsRanges() {
        val content = ByteArray(4096) { (it % 251).toByte() }
        val rangeRequests = AtomicInteger()
        withServer(content, true, rangeRequests) { url ->
            val output = temp.newFile("parallel.bin")
            DirectDownloader().download(url, output, connections = 4)
            assertArrayEquals(content, output.readBytes())
            assertTrue("Expected a probe and multiple range requests", rangeRequests.get() >= 5)
        }
    }

    @Test
    fun downloadsWholeFileWhenServerIgnoresRanges() {
        val content = ByteArray(2048) { (it % 239).toByte() }
        withServer(content, false, AtomicInteger()) { url ->
            val output = temp.newFile("whole.bin")
            DirectDownloader().download(url, output, connections = 4)
            assertArrayEquals(content, output.readBytes())
        }
    }

    @Test
    fun downloadsWholeFileWhenServerRejectsRangeProbe() {
        val content = ByteArray(2048) { (it % 239).toByte() }
        for (status in listOf(400, 403, 405, 416, 501)) {
            val ranges = AtomicInteger()
            val whole = AtomicInteger()
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext("/file") { exchange ->
                if (exchange.requestHeaders.getFirst("Range") != null) {
                    ranges.incrementAndGet()
                    exchange.sendResponseHeaders(status, -1)
                } else {
                    whole.incrementAndGet()
                    exchange.sendResponseHeaders(200, content.size.toLong())
                    exchange.responseBody.use { it.write(content) }
                }
                exchange.close()
            }
            server.start()
            try {
                val output = temp.newFile("whole-$status.bin")
                DirectDownloader(OkHttpClient(), retryDelay = { }).download("http://127.0.0.1:${server.address.port}/file", output)
                assertArrayEquals(content, output.readBytes())
                assertEquals("Range probe for HTTP $status", 1, ranges.get())
                assertEquals("Whole download for HTTP $status", 1, whole.get())
            } finally {
                server.stop(0)
            }
        }
    }

    @Test
    fun reportsProgressUpToTheCompleteSize() {
        val content = ByteArray(2048) { (it % 239).toByte() }
        val updates = Collections.synchronizedList(mutableListOf<Long>())
        withServer(content, true, AtomicInteger()) { url ->
            val output = temp.newFile("progress.bin")
            DirectDownloader().download(url, output, connections = 4) { received, _ -> updates.add(received) }
            assertTrue(updates.isNotEmpty())
            assertTrue(updates.zipWithNext().all { (first, second) -> second >= first })
            assertTrue(updates.last() == content.size.toLong())
        }
    }

    @Test
    fun doesNotMixPartsWhenTheRemoteFileChanges() {
        val original = ByteArray(2048) { 1 }
        val replacement = ByteArray(2048) { 2 }
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val executor = Executors.newCachedThreadPool()
        server.executor = executor
        server.createContext("/file") { exchange ->
            val range = exchange.requestHeaders.getFirst("Range")
            val bounds = Regex("bytes=(\\d+)-(\\d+)").matchEntire(range.orEmpty())
            when {
                range == "bytes=0-0" -> {
                    exchange.responseHeaders.add("ETag", "\"old\"")
                    exchange.responseHeaders.add("Content-Range", "bytes 0-0/${original.size}")
                    exchange.sendResponseHeaders(206, 1)
                    exchange.responseBody.use { it.write(original, 0, 1) }
                }
                bounds != null && exchange.requestHeaders.getFirst("If-Range") != "\"old\"" -> {
                    val start = bounds.groupValues[1].toInt()
                    val end = bounds.groupValues[2].toInt()
                    val data = if (start == 0) original else replacement
                    exchange.responseHeaders.add("Content-Range", "bytes $start-$end/${data.size}")
                    exchange.sendResponseHeaders(206, (end - start + 1).toLong())
                    exchange.responseBody.use { it.write(data, start, end - start + 1) }
                }
                else -> {
                    exchange.sendResponseHeaders(200, replacement.size.toLong())
                    exchange.responseBody.use { it.write(replacement) }
                }
            }
        }
        server.start()
        try {
            val output = temp.newFile("changed.bin")
            DirectDownloader().download("http://127.0.0.1:${server.address.port}/file", output, connections = 4)
            assertArrayEquals(replacement, output.readBytes())
        } finally {
            server.stop(0)
            executor.shutdownNow()
        }
    }

    private fun withServer(content: ByteArray, ranges: Boolean, count: AtomicInteger, test: (String) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val executor = Executors.newCachedThreadPool()
        server.executor = executor
        server.createContext("/file") { exchange ->
            val range = exchange.requestHeaders.getFirst("Range")
            if (range != null) count.incrementAndGet()
            val bounds = Regex("bytes=(\\d+)-(\\d+)").matchEntire(range.orEmpty())
            if (ranges && bounds != null) {
                val start = bounds.groupValues[1].toInt()
                val end = bounds.groupValues[2].toInt().coerceAtMost(content.lastIndex)
                val body = content.copyOfRange(start, end + 1)
                exchange.responseHeaders.add("ETag", "\"fixed\"")
                exchange.responseHeaders.add("Content-Range", "bytes $start-$end/${content.size}")
                exchange.sendResponseHeaders(206, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            } else {
                exchange.sendResponseHeaders(200, content.size.toLong())
                exchange.responseBody.use { it.write(content) }
            }
        }
        server.start()
        try {
            test("http://127.0.0.1:${server.address.port}/file")
        } finally {
            server.stop(0)
            executor.shutdownNow()
        }
    }
}
