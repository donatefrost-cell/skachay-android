package ru.skachay.app.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.skachay.app.choice.AvailableFormat
import ru.skachay.app.choice.VideoPreview
import ru.skachay.app.recipes.InstagramRecipe
import ru.skachay.app.recipes.Recipes
import ru.skachay.app.recipes.TikTokRecipe

class SocialFallbackPolicyTest {
    @Test
    fun socialYtDlpFallbackUsesEachProviderSwitch() {
        assertTrue(SocialFallbackPolicy.ytDlpEnabled("https://www.instagram.com/reel/ABC123/", Recipes()))
        assertFalse(
            SocialFallbackPolicy.ytDlpEnabled(
                "https://www.instagram.com/reel/ABC123/",
                Recipes(instagram = InstagramRecipe(ytDlpEnabled = false)),
            ),
        )
        assertTrue(SocialFallbackPolicy.ytDlpEnabled("https://www.tiktok.com/@sample/video/12345", Recipes()))
        assertFalse(
            SocialFallbackPolicy.ytDlpEnabled(
                "https://www.tiktok.com/@sample/video/12345",
                Recipes(tiktok = TikTokRecipe(ytDlpEnabled = false)),
            ),
        )
        assertFalse(SocialFallbackPolicy.ytDlpEnabled("https://example.com/video", Recipes()))
    }

    @Test
    fun previewFallbackSelectsMuxedGalleryCompatibleVideoAndKeepsHeaders() {
        val preview =
            VideoPreview(
                "clip",
                null,
                12,
                null,
                listOf(
                    AvailableFormat(
                        "video-only",
                        "mp4",
                        "avc1.4d401f",
                        null,
                        1080,
                        30,
                        null,
                        null,
                        videoBitrate = 4_000,
                        directUrl = "https://cdn.example/video-only.mp4",
                    ),
                    AvailableFormat(
                        "webm",
                        "webm",
                        "av01",
                        "opus",
                        2160,
                        30,
                        256,
                        null,
                        videoBitrate = 8_000,
                        directUrl = "https://cdn.example/muxed.webm",
                    ),
                    AvailableFormat(
                        "mp4",
                        "mp4",
                        "avc1.4d401f",
                        "mp4a.40.2",
                        720,
                        30,
                        128,
                        null,
                        videoBitrate = 2_000,
                        directUrl = "https://cdn.example/muxed.mp4",
                        httpHeaders = mapOf("Referer" to "https://www.instagram.com/"),
                    ),
                ),
            )

        val selected = SocialYtDlpResolver.select(preview).single()

        assertEquals("https://cdn.example/muxed.mp4", selected.url)
        assertEquals(ResolvedMedia.Kind.VIDEO, selected.type)
        assertEquals("https://www.instagram.com/", selected.headers["Referer"])
    }
}
