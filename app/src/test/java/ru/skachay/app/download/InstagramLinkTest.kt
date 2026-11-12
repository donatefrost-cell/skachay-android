package ru.skachay.app.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramLinkTest {
    @Test
    fun extractsShortcodeWithoutTrackingParameters() {
        assertEquals("Ab_12-Z", InstagramLink.shortcode("https://www.instagram.com/reel/Ab_12-Z/?igsh=secret"))
        assertEquals("abc", InstagramLink.shortcode("https://instagram.com/p/abc/"))
        assertEquals("abc", InstagramLink.shortcode("https://instagr.am/tv/abc"))
        assertEquals("abc", InstagramLink.shortcode("https://instagram.com/reels/abc"))
        assertEquals("abc", InstagramLink.shortcode("https://instagram.com/author/reel/abc/?igsh=tracking"))
        assertEquals("https://www.instagram.com/p/Ab_12-Z/", InstagramLink.canonical("https://www.instagram.com/reel/Ab_12-Z/?igsh=secret"))
        assertTrue(InstagramLink.isVideoPost("https://instagram.com/author/reel/abc/"))
        assertFalse(InstagramLink.isVideoPost("https://instagram.com/p/abc/"))
    }

    @Test
    fun rejectsLookalikeDomainsAndInvalidPaths() {
        assertNull(InstagramLink.shortcode("https://instagram.com.example.org/p/abc"))
        assertNull(InstagramLink.shortcode("https://instagram.com/accounts/login/"))
        assertNull(InstagramLink.shortcode("https://instagram.com/p/abc%2Fdef"))
        assertFalse(InstagramLink.supports("https://user@instagram.com/p/abc"))
        assertFalse(InstagramLink.supports("https://instagram.com:8443/p/abc"))
    }
}
