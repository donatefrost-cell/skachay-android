package ru.skachay.app.download

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.skachay.app.DownloadService
import ru.skachay.app.MainActivity

@RunWith(AndroidJUnit4::class)
class RetryFileNameInstrumentedTest {
    @Test
    fun retryKeepsTheChosenFileName() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val main = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val manager = context.getSystemService(NotificationManager::class.java)
        assertTrue(manager.areNotificationsEnabled())
        val history = context.getSharedPreferences("downloads", Context.MODE_PRIVATE)
        val savedFiles = context.getSharedPreferences("download_files", Context.MODE_PRIVATE)
        val name = "RetryName_${UUID.randomUUID()}"
        var saved: Uri? = null
        var testedUrl: String? = null
        var notificationId: Int? = null
        try {
            RetryServer().use { server ->
                val url = "http://127.0.0.1:${server.port}/photo.jpg"
                testedUrl = url
                val started = System.currentTimeMillis()
                context.startForegroundService(Intent(context, DownloadService::class.java)
                    .putExtra(DownloadService.EXTRA_URL, url).putExtra(DownloadService.EXTRA_FILE_NAME, name))
                val deadline = SystemClock.elapsedRealtime() + 15_000
                var failure: Notification? = null
                while (SystemClock.elapsedRealtime() < deadline) {
                    val entry = manager.activeNotifications.firstOrNull {
                        it.postTime >= started && it.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() == "Не получилось скачать"
                    }
                    if (entry != null) { failure = entry.notification; notificationId = entry.id; break }
                    Thread.sleep(100)
                }
                assertNotNull("Expected an error notification", failure)
                val retry = failure!!.actions.orEmpty().firstOrNull { it.title.toString() == "Повторить" }
                assertNotNull("Expected a retry action", retry)
                retry!!.actionIntent.send()
                val retryDeadline = SystemClock.elapsedRealtime() + 20_000
                while (SystemClock.elapsedRealtime() < retryDeadline) {
                    saved = history.getString(url, null)?.let(Uri::parse)
                    if (saved != null) break
                    Thread.sleep(100)
                }
                assertNotNull("Expected the retried download to be saved", saved)
                context.contentResolver.query(saved!!, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)!!.use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("$name.jpg", cursor.getString(0))
                }
            }
        } finally {
            testedUrl?.let { history.edit().remove(it).commit() }
            saved?.let { uri ->
                savedFiles.edit().remove(uri.toString()).commit()
                context.contentResolver.delete(uri, null, null)
            }
            notificationId?.let(manager::cancel)
            main.finish()
        }
    }

    private class RetryServer : AutoCloseable {
        private val server = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
        private val executor = Executors.newSingleThreadExecutor()
        private val requests = AtomicInteger()
        val port: Int = server.localPort

        init {
            executor.execute {
                while (!server.isClosed) {
                    val socket = try { server.accept() } catch (_: SocketException) { break }
                    socket.use { connection ->
                        val reader = connection.getInputStream().bufferedReader(Charsets.US_ASCII)
                        while (true) { if (reader.readLine().isNullOrEmpty()) break }
                        val first = requests.getAndIncrement() == 0
                        val bytes = if (first) byteArrayOf() else byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xd9.toByte())
                        val status = if (first) "404 Not Found" else "200 OK"
                        val output = connection.getOutputStream()
                        output.write("HTTP/1.1 $status\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
                        output.write(bytes)
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
