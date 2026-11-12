package ru.skachay.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedUrlTest {
    @Test
    fun extractsTheFirstLinkFromSharedText() {
        assertEquals(
            "https://www.instagram.com/p/abc/?igsh=123",
            SharedUrl.firstIn("Посмотри https://www.instagram.com/p/abc/?igsh=123 и https://example.com"),
        )
    }

    @Test
    fun removesClosingPunctuationAroundLink() {
        assertEquals("https://youtu.be/abc", SharedUrl.firstIn("Посмотри (https://youtu.be/abc)."))
    }

    @Test
    fun ignoresMissingInvalidAndUnsupportedLinks() {
        assertNull(SharedUrl.firstIn(null))
        assertNull(SharedUrl.firstIn("Без ссылки ftp://example.com/file"))
        assertNull(SharedUrl.firstIn("https://"))
    }

    @Test
    fun skipsInvalidCandidatesBeforeAValidLink() {
        assertEquals(
            "https://www.instagram.com/p/a",
            SharedUrl.firstIn("http:// https://example.com/a https://www.instagram.com/p/a"),
        )
    }

    @Test
    fun extractsDistinctLinksForBatchDownloads() {
        assertEquals(
            listOf("https://youtu.be/a", "https://www.instagram.com/p/b"),
            SharedUrl.allIn("https://youtu.be/a, https://www.instagram.com/p/b. https://youtu.be/a"),
        )
    }

    @Test
    fun acceptsOnlySupportedProviderHostsAndTheirRealSubdomains() {
        assertEquals("https://m.youtube.com/watch?v=x", SharedUrl.firstIn("https://m.youtube.com/watch?v=x"))
        assertEquals("https://vm.tiktok.com/abc", SharedUrl.firstIn("https://vm.tiktok.com/abc"))
        assertEquals("https://instagr.am/p/abc", SharedUrl.firstIn("https://instagr.am/p/abc"))
        assertNull(SharedUrl.firstIn("https://youtube.com.attacker.example/watch?v=x"))
        assertNull(SharedUrl.firstIn("https://youtube.attacker.example/watch?v=x"))
        assertNull(SharedUrl.firstIn("https://example.com/video"))
    }

    @Test
    fun rejectsOverlongUrlsUserInfoAndNonWebPorts() {
        assertNull(SharedUrl.firstIn("https://www.youtube.com/" + "a".repeat(2048)))
        assertNull(SharedUrl.firstIn("https://user:pass@www.youtube.com/watch?v=x"))
        assertNull(SharedUrl.firstIn("https://www.youtube.com:8080/watch?v=x"))
        assertEquals("https://youtu.be/x", SharedUrl.firstIn("http://youtu.be/x"))
        assertEquals("https://youtu.be/x", SharedUrl.firstIn("http://youtu.be:80/x"))
    }

    @Test
    fun capsExtractedBatchAtTwentyLinks() {
        val text = (1..25).joinToString(" ") { "https://youtu.be/$it" }
        assertEquals(20, SharedUrl.allIn(text).size)
        assertEquals("https://youtu.be/20", SharedUrl.allIn(text).last())
    }

    @Test
    fun rejectsOversizedSharedTextBeforeScanning() {
        assertNull(SharedUrl.firstIn("x".repeat(65_537) + " https://youtu.be/video"))
    }
}
