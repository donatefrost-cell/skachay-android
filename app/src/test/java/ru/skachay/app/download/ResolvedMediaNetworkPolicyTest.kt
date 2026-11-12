package ru.skachay.app.download

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolvedMediaNetworkPolicyTest {
    @Test
    fun acceptsOnlyTheSourcePlatformsMediaDomains() {
        assertTrue(ResolvedMediaNetworkPolicy.isAllowed("https://youtu.be/video", "https://rr1.googlevideo.com/video"))
        assertTrue(ResolvedMediaNetworkPolicy.isAllowed("https://instagram.com/p/post", "https://scontent.cdninstagram.com/photo"))
        assertTrue(ResolvedMediaNetworkPolicy.isAllowed("https://tiktok.com/@user/video/1", "https://v16.tiktokcdn-us.com/video"))
    }

    @Test
    fun rejectsHostLookalikesAndCrossPlatformMedia() {
        assertFalse(ResolvedMediaNetworkPolicy.isAllowed("https://youtu.be/video", "https://googlevideo.com.attacker.test/video"))
        assertFalse(ResolvedMediaNetworkPolicy.isAllowed("https://youtu.be/video", "https://notgooglevideo.com/video"))
        assertFalse(ResolvedMediaNetworkPolicy.isAllowed("https://instagram.com/p/post", "https://instagram.com.attacker.test/video"))
        assertFalse(ResolvedMediaNetworkPolicy.isAllowed("https://instagram.com/p/post", "https://eviltiktokcdn.com/video"))
        assertFalse(ResolvedMediaNetworkPolicy.isAllowed("https://tiktok.com/@user/video/1", "https://127.0.0.1/video"))
        assertFalse(ResolvedMediaNetworkPolicy.isAllowed("https://example.com/video", "https://example.com/video"))
    }
}
