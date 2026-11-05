package ru.skachay.app.updates

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdateDownloaderInstrumentedTest {
    @Test
    fun downloadsTheInstalledApkAndVerifiesItsRealSignature() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val installedApk = File(context.applicationInfo.sourceDir)
        val version = ReleaseStore.currentVersion(context)
        val downloader = UpdateDownloader(context)
        assertTrue(downloader.validateArchive(installedApk, version))
        assertFalse(downloader.validateArchive(installedApk, version + 1))
        LocalApkServer(installedApk).use { server ->
            val uri = downloader.download(ReleaseInfo("test", version, "http://127.0.0.1:${server.port}/app.apk"))
            assertEquals("content", uri.scheme)
            context.contentResolver.openInputStream(uri)!!.use { input ->
                assertEquals('P'.code, input.read())
                assertEquals('K'.code, input.read())
            }
        }
        File(context.cacheDir, "updates/update.apk").delete()
    }

    private class LocalApkServer(private val file: File) : AutoCloseable {
        private val server = ServerSocket(0, 2, InetAddress.getByName("127.0.0.1"))
        private val executor = Executors.newSingleThreadExecutor()
        val port: Int = server.localPort

        init {
            executor.execute {
                while (!server.isClosed) {
                    val socket = try { server.accept() } catch (_: SocketException) { break }
                    socket.use { connection ->
                        val reader = connection.getInputStream().bufferedReader(Charsets.US_ASCII)
                        var range = false
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                            if (line.startsWith("Range:", ignoreCase = true)) range = true
                        }
                        val size = if (range) 0 else file.length()
                        val output = connection.getOutputStream()
                        output.write("HTTP/1.1 200 OK\r\nContent-Length: $size\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
                        if (!range) file.inputStream().use { it.copyTo(output) }
                        output.flush()
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
