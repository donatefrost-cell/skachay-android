package ru.skachay.app.download

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
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
