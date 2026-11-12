package ru.skachay.app.network

import okhttp3.ResponseBody
import java.io.ByteArrayOutputStream

internal fun ResponseBody.readUtf8Bounded(maxBytes: Int): String? {
    if (contentLength() > maxBytes) return null
    val output = ByteArrayOutputStream()
    byteStream().use { input ->
        val buffer = ByteArray(4096)
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            if (output.size() + count > maxBytes) return null
            output.write(buffer, 0, count)
        }
    }
    return output.toString(Charsets.UTF_8.name())
}
