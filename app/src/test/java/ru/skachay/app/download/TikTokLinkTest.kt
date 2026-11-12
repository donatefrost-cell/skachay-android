package ru.skachay.app.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TikTokLinkTest {
    @Test
    fun acceptsVideoPhotoAndShortLinks() {
        assertTrue(TikTokLink.supports("https://vm.tiktok.com/abc/"))
        assertTrue(TikTokLink.supports("https://www.tiktok.com/@name/photo/123?tracking=1"))
        assertEquals("123", TikTokLink.postId("https://www.tiktok.com/@name/photo/123?tracking=1"))
        assertEquals("456", TikTokLink.postId("https://www.tiktok.com/@name/video/456"))
        assertTrue(TikTokLink.isPhoto("https://www.tiktok.com/@name/photo/123"))
    }

    @Test
    fun rejectsLookalikeDomains() {
        assertFalse(TikTokLink.supports("https://tiktok.com.example.org/@x/video/123"))
        assertNull(TikTokLink.postId("https://vm.tiktok.com/abc"))
        assertFalse(TikTokLink.supports("https://user@tiktok.com/@x/video/123"))
        assertFalse(TikTokLink.supports("https://tiktok.com:8443/@x/video/123"))
    }
}
