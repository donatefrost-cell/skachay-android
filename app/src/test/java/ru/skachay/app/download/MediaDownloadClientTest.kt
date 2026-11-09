package ru.skachay.app.download

import okhttp3.Protocol
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaDownloadClientTest {
    @Test
    fun sharedMediaClientNegotiatesHttp2AndHttp11() {
        assertTrue(MediaDownloadClient.client.protocols.contains(Protocol.HTTP_2))
        assertTrue(MediaDownloadClient.client.protocols.contains(Protocol.HTTP_1_1))
    }
}
