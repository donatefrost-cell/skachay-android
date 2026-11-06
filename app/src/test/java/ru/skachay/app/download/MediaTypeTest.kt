package ru.skachay.app.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaTypeTest {
    @Test
    fun recognisesImagesByTheirContents() {
        assertEquals("image/jpeg", MediaType.detect(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte()), ""))
        assertEquals("image/png", MediaType.detect(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a), ""))
    }

    @Test
    fun recognisesMp4VideoAndAudio() {
        val header = byteArrayOf(0, 0, 0, 24, 0x66, 0x74, 0x79, 0x70)
        assertEquals("video/mp4", MediaType.detect(header, "https://example.com/a.mp4"))
        assertEquals("audio/mp4", MediaType.detect(header, "https://example.com/a.m4a"))
    }

    @Test
    fun recognisesSelectedAudioInsideWebmAndMp4Containers() {
        val webm = byteArrayOf(0x1a, 0x45, 0xdf.toByte(), 0xa3.toByte())
        val mp4 = byteArrayOf(0, 0, 0, 24, 0x66, 0x74, 0x79, 0x70)
        assertNull(MediaType.detect(webm, "video.webm", audioOnly = true))
        assertEquals("video/webm", MediaType.detect(webm, "video.webm"))
        assertEquals("audio/mp4", MediaType.detect(mp4, "video.mp4", audioOnly = true))
        assertEquals("audio/ogg", MediaType.detect("OggS".toByteArray(), "audio.opus", audioOnly = true))
    }

    @Test
    fun rejectsAWebPageInsteadOfSavingItAsMedia() {
        assertNull(MediaType.detect("<html>".toByteArray(), "https://example.com/a.mp4"))
    }
}
