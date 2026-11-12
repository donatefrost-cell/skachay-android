package ru.skachay.app.download

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceTypeTest {
    @Test
    fun recognisesYoutubeHostsAndSubdomains() {
        assertTrue(SourceType.isYouTube("https://youtu.be/abc"))
        assertTrue(SourceType.isYouTube("https://m.youtube.com/shorts/abc"))
        assertTrue(SourceType.isYouTube("https://youtube.com/watch?v=abc"))
    }

    @Test
    fun rejectsLookalikeHosts() {
        assertFalse(SourceType.isYouTube("https://youtube.com.example.org/watch?v=abc"))
        assertFalse(SourceType.isYouTube("https://notyoutube.com/watch?v=abc"))
        assertFalse(SourceType.isYouTube("not a link"))
        assertFalse(SourceType.isYouTube("https://user@youtube.com/watch?v=abc"))
        assertFalse(SourceType.isYouTube("https://youtube.com:8443/watch?v=abc"))
        assertFalse(SourceType.isYouTube("https://youtube.com/" + "x".repeat(2048)))
    }
}
