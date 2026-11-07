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
        assertEquals("https://example.com/a", SharedUrl.firstIn("http:// https://example.com/a"))
    }

    @Test
    fun extractsDistinctLinksForBatchDownloads() {
        assertEquals(
            listOf("https://youtu.be/a", "https://www.instagram.com/p/b"),
            SharedUrl.allIn("https://youtu.be/a, https://www.instagram.com/p/b. https://youtu.be/a"),
        )
    }
}
