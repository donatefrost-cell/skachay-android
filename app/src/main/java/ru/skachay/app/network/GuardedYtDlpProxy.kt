package ru.skachay.app.network

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import ru.skachay.app.download.InstagramLink
import ru.skachay.app.download.ResolvedMediaNetworkPolicy
import ru.skachay.app.download.SourceType
import ru.skachay.app.download.TikTokLink
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Applies the app's SSRF policy to each HTTPS CONNECT yt-dlp opens, including redirects. */
internal class GuardedYtDlpProxy(
    private val sourceUrl: String,
) : Closeable {
    private val server = ServerSocket(0, MAX_CONNECTIONS, InetAddress.getByName("127.0.0.1"))
    private val running = AtomicInteger(1)
    private val activeSockets = ConcurrentSocketSet()
    private val workers =
        ThreadPoolExecutor(
            0,
            MAX_CONNECTIONS,
            30,
            TimeUnit.SECONDS,
            SynchronousQueue(),
            NamedDaemonThreadFactory("skachay-yt-proxy"),
            ThreadPoolExecutor.AbortPolicy(),
        )
    private val acceptThread = NamedDaemonThreadFactory("skachay-yt-proxy-accept").newThread(::acceptConnections)

    val url: String = "http://127.0.0.1:${server.localPort}"

    init {
        require(YtDlpHostPolicy.allowedSuffixes(sourceUrl).isNotEmpty()) { "Unsupported source for guarded media extraction" }
        acceptThread.start()
    }

    override fun close() {
        if (running.getAndSet(0) == 0) return
        runCatching { server.close() }
        activeSockets.closeAll()
        workers.shutdownNow()
        acceptThread.interrupt()
    }

    private fun acceptConnections() {
        while (running.get() == 1) {
            val client =
                try {
                    server.accept().apply { soTimeout = SOCKET_TIMEOUT_MS }
                } catch (_: SocketException) {
                    return
                } catch (_: IOException) {
                    if (running.get() == 0) return else continue
                }
            activeSockets.add(client)
            try {
                workers.execute {
                    try {
                        handle(client)
                    } catch (_: IOException) {
                        // A cancelled yt-dlp process can close its proxy socket mid-transfer.
                    } finally {
                        activeSockets.remove(client)
                        runCatching { client.close() }
                    }
                }
            } catch (_: RejectedExecutionException) {
                activeSockets.remove(client)
                runCatching { client.close() }
            }
        }
    }

    private fun handle(client: Socket) {
        val input = client.getInputStream()
        val requestLine = readLine(input) ?: return reject(client, 400)
        val match = CONNECT_LINE.matchEntire(requestLine) ?: return reject(client, 405)
        var totalHeaderBytes = requestLine.length
        while (true) {
            val header = readLine(input) ?: return reject(client, 400)
            totalHeaderBytes += header.length
            if (totalHeaderBytes > MAX_HEADER_BYTES) return reject(client, 431)
            if (header.isEmpty()) break
        }

        val target = "https://${match.groupValues[1]}".toHttpUrlOrNull() ?: return reject(client, 403)
        if (!YtDlpHostPolicy.isAllowedConnectHost(sourceUrl, target.host, target.port)) return reject(client, 403)
        val remote = connectToPublicHost(target.host, target.port) ?: return reject(client, 502)
        activeSockets.add(remote)
        try {
            remote.use {
                client.soTimeout = SOCKET_TIMEOUT_MS
                it.soTimeout = SOCKET_TIMEOUT_MS
                val output = client.getOutputStream()
                output.write("HTTP/1.1 200 Connection Established\r\nProxy-Agent: Skachay\r\n\r\n".toByteArray(Charsets.US_ASCII))
                output.flush()
                tunnel(client, it)
            }
        } finally {
            activeSockets.remove(remote)
        }
    }

    private fun connectToPublicHost(
        host: String,
        port: Int,
    ): Socket? {
        val addresses =
            try {
                InetAddress.getAllByName(host).distinct()
            } catch (_: IOException) {
                return null
            }
        if (addresses.isEmpty() || addresses.any { !UrlNetworkPolicy.isPublicAddress(it) }) return null
        addresses.forEach { address ->
            val socket = Socket()
            try {
                socket.connect(InetSocketAddress(address, port), CONNECT_TIMEOUT_MS)
                socket.soTimeout = SOCKET_TIMEOUT_MS
                return socket
            } catch (_: IOException) {
                runCatching { socket.close() }
            }
        }
        return null
    }

    private fun tunnel(
        client: Socket,
        remote: Socket,
    ) {
        val finished = CountDownLatch(2)

        fun forward(
            source: Socket,
            destination: Socket,
        ) {
            try {
                source.getInputStream().copyTo(destination.getOutputStream(), bufferSize = 32 * 1024)
            } catch (_: IOException) {
                runCatching { source.close() }
                runCatching { destination.close() }
            } finally {
                runCatching { destination.shutdownOutput() }
                finished.countDown()
            }
        }
        NamedDaemonThreadFactory("skachay-yt-proxy-up").newThread { forward(client, remote) }.start()
        NamedDaemonThreadFactory("skachay-yt-proxy-down").newThread { forward(remote, client) }.start()
        try {
            finished.await()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            runCatching { client.close() }
            runCatching { remote.close() }
        }
    }

    private fun reject(
        client: Socket,
        status: Int,
    ) {
        runCatching {
            val phrase = if (status == 502) "Bad Gateway" else "Request Rejected"
            client.getOutputStream().write(
                "HTTP/1.1 $status $phrase\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII),
            )
        }
    }

    private fun readLine(input: java.io.InputStream): String? {
        val bytes = ByteArrayOutputStream()
        while (bytes.size() <= MAX_HEADER_LINE_BYTES) {
            val byte = input.read()
            if (byte < 0) return if (bytes.size() == 0) null else throw IOException("Incomplete proxy header")
            if (byte == '\n'.code) return bytes.toString(Charsets.ISO_8859_1.name()).trimEnd('\r')
            bytes.write(byte)
        }
        throw IOException("Proxy header line too long")
    }

    private class ConcurrentSocketSet {
        private val sockets =
            java.util.concurrent.ConcurrentHashMap
                .newKeySet<Socket>()

        fun add(socket: Socket) {
            sockets += socket
        }

        fun remove(socket: Socket) {
            sockets -= socket
        }

        fun closeAll() {
            sockets.forEach { runCatching { it.close() } }
            sockets.clear()
        }
    }

    private class NamedDaemonThreadFactory(
        private val name: String,
    ) : ThreadFactory {
        private val count = AtomicInteger()

        override fun newThread(runnable: Runnable): Thread = Thread(runnable, "$name-${count.incrementAndGet()}").apply { isDaemon = true }
    }

    private companion object {
        const val MAX_CONNECTIONS = 16
        const val MAX_HEADER_BYTES = 32 * 1024
        const val MAX_HEADER_LINE_BYTES = 8 * 1024
        const val CONNECT_TIMEOUT_MS = 20_000
        const val SOCKET_TIMEOUT_MS = 120_000
        val CONNECT_LINE = Regex("CONNECT\\s+(\\S+)\\s+HTTP/1\\.[01]", RegexOption.IGNORE_CASE)
    }
}

internal object YtDlpHostPolicy {
    private val youtubeSuffixes =
        setOf(
            "youtube.com",
            "youtu.be",
            "youtube-nocookie.com",
            "googlevideo.com",
            "ytimg.com",
            "ggpht.com",
            "youtubei.googleapis.com",
            "youtube.googleapis.com",
            "googleusercontent.com",
        )

    fun allowedSuffixes(sourceUrl: String): Set<String> =
        when {
            SourceType.isYouTube(sourceUrl) -> youtubeSuffixes
            InstagramLink.supports(sourceUrl) -> requireNotNull(ResolvedMediaNetworkPolicy.redirectHostSuffixes(sourceUrl))
            TikTokLink.supports(sourceUrl) -> requireNotNull(ResolvedMediaNetworkPolicy.redirectHostSuffixes(sourceUrl))
            else -> emptySet()
        }

    fun isAllowedConnectHost(
        sourceUrl: String,
        host: String,
        port: Int,
    ): Boolean {
        val source = sourceUrl.toHttpUrlOrNull() ?: return false
        val target = "https://$host".toHttpUrlOrNull() ?: return false
        val allowed = allowedSuffixes(sourceUrl)
        return port == 443 &&
            UrlNetworkPolicy.isSafeHttpsUrl(source) &&
            UrlNetworkPolicy.isSafeHttpsUrl(target) &&
            allowed.any { UrlNetworkPolicy.hostMatchesSuffix(target.host, it) }
    }
}
