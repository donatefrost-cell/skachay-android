package ru.skachay.app.choice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import ru.skachay.app.download.ResolvedMedia

class SocialPreviewCodecTest {
    private val items =
        listOf(
            ResolvedMedia("https://example.com/first.jpg", ResolvedMedia.Kind.IMAGE, mapOf("User-Agent" to "agent")),
            ResolvedMedia("https://example.com/second.mp4", ResolvedMedia.Kind.VIDEO, mapOf("Cookie" to "session=value")),
            ResolvedMedia("https://example.com/third.jpg", ResolvedMedia.Kind.IMAGE),
        )
    private val source = "a".repeat(64)
    private val id = "7d881574-6b36-4d72-a3bb-86d813b8845a"

    @Test
    fun storesItemsForTheSameSourceWithoutDroppingHeaders() {
        val json = SocialPreviewCodec.encode(source, id, 10_000L, items)
        val snapshot = SocialPreviewCodec.decode(json, source, 10_500L)
        assertEquals(id, snapshot?.id)
        assertEquals(items.map { it.copy(headers = it.headers - "Cookie") }, snapshot?.items)
        assertFalse(json.contains("session=value"))
        assertNull(SocialPreviewCodec.decode(json, "b".repeat(64), 10_500L))
        assertNull(SocialPreviewCodec.decode(json, source, 10_000L + 60 * 60 * 1000L + 1))
        assertNull(SocialPreviewCodec.decode(json, source, 9_999L))
    }

    @Test
    fun rejectsInvalidSnapshotsAndUnsafeAddresses() {
        assertNull(SocialPreviewCodec.decode("{", source, 10_000L))
        val unsafe = items.toMutableList().apply { this[0] = this[0].copy(url = "https://name:password@example.com/a.jpg") }
        assertNull(SocialPreviewCodec.decode(SocialPreviewCodec.encode(source, id, 10_000L, unsafe), source, 10_000L))
        val privateAddress = items.toMutableList().apply { this[0] = this[0].copy(url = "https://169.254.169.254/latest/meta-data") }
        assertNull(SocialPreviewCodec.decode(SocialPreviewCodec.encode(source, id, 10_000L, privateAddress), source, 10_000L))
        val cleartext = items.toMutableList().apply { this[0] = this[0].copy(url = "http://example.com/a.jpg") }
        assertNull(SocialPreviewCodec.decode(SocialPreviewCodec.encode(source, id, 10_000L, cleartext), source, 10_000L))
        val brokenHeader = items.toMutableList().apply { this[0] = this[0].copy(headers = mapOf("Referer" to "x\r\ny")) }
        assertNull(SocialPreviewCodec.decode(SocialPreviewCodec.encode(source, id, 10_000L, brokenHeader), source, 10_000L))
    }

    @Test
    fun selectsOnlyRequestedItemsAndRejectsInvalidSelections() {
        assertEquals(listOf(items[0], items[2]), SocialSelection.items(items, intArrayOf(2, 0)))
        assertNull(SocialSelection.items(items, intArrayOf()))
        assertNull(SocialSelection.items(items, intArrayOf(0, 0)))
        assertNull(SocialSelection.items(items, intArrayOf(-1)))
        assertNull(SocialSelection.items(items, intArrayOf(3)))
        assertEquals("post", SocialSelection.historyKey("post", intArrayOf(2, 0, 1), 3))
        assertEquals("post::items:0,2", SocialSelection.historyKey("post", intArrayOf(2, 0), 3))
    }
}
