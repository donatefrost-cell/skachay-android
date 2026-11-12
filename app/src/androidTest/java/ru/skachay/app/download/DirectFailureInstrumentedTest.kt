package ru.skachay.app.download

import android.content.Intent
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.skachay.app.DownloadService
import ru.skachay.app.MainActivity
import ru.skachay.app.diagnostics.DiagnosticStatus
import ru.skachay.app.diagnostics.DiagnosticsStore
import ru.skachay.app.diagnostics.DownloadProvider
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class DirectFailureInstrumentedTest {
    @Test
    fun failedDirectRequestDoesNotStartTheVideoFallback() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            NotFoundServer().use { server ->
                val started = System.currentTimeMillis()
                context.startForegroundService(
                    Intent(context, DownloadService::class.java)
                        .putExtra(DownloadService.EXTRA_URL, "http://127.0.0.1:${server.port}/missing.jpg"),
                )
                val deadline = SystemClock.elapsedRealtime() + 20_000
                var failed = false
                while (SystemClock.elapsedRealtime() < deadline) {
                    val entry = DiagnosticsStore.read(context).first { it.provider == DownloadProvider.OTHER }
                    if (entry.timestamp >= started && entry.status == DiagnosticStatus.FAILED) {
                        failed = true
                        break
                    }
                    Thread.sleep(100)
                }
                assertTrue("The direct request should finish with a visible error", failed)
                assertEquals("An HTTP error must not trigger a second extractor request", 1, server.requests.get())
            }
        } finally {
            activity.finish()
        }
    }

    private class NotFoundServer : AutoCloseable {
        private val server = ServerSocket(0, 2, InetAddress.getByName("127.0.0.1"))
        private val executor = Executors.newSingleThreadExecutor()
        val port: Int = server.localPort
        val requests = AtomicInteger()

        init {
            executor.execute {
                while (!server.isClosed) {
                    val socket =
                        try {
                            server.accept()
                        } catch (_: SocketException) {
                            break
                        }
                    socket.use { connection ->
                        val reader = connection.getInputStream().bufferedReader(Charsets.US_ASCII)
                        while (true) {
                            if (reader.readLine().isNullOrEmpty()) break
                        }
                        requests.incrementAndGet()
                        connection.getOutputStream().write(
                            "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII),
                        )
                    }
                }
            }
        }

        override fun close() {
            server.close()
            executor.shutdown()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }
    }
}
