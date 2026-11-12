package ru.skachay.app.download

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class RetryConfigurationTest {
    @get:Rule val temp = TemporaryFolder()

    @Test
    fun configuredRetryCountControlsRequestsWithoutNetwork() {
        for (retries in listOf(0, 1, 3)) {
            val attempts = AtomicInteger()
            val waits = mutableListOf<Long>()
            val client =
                OkHttpClient
                    .Builder()
                    .addInterceptor { chain ->
                        attempts.incrementAndGet()
                        Response
                            .Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(503)
                            .message("Unavailable")
                            .body(ByteArray(0).toResponseBody())
                            .build()
                    }.build()
            val output = temp.newFile("retry-$retries.jpg")
            assertThrows(IOException::class.java) {
                DirectDownloader(client, retryDelay = { waits.add(it) })
                    .download("https://example.com/file.jpg", output, retries = retries)
            }
            assertEquals(retries + 1, attempts.get())
            assertEquals(retries, waits.size)
        }
    }
}
