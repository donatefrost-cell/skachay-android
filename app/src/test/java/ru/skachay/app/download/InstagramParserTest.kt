package ru.skachay.app.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.skachay.app.recipes.InstagramRecipe

class InstagramParserTest {
    @Test
    fun readsFieldsSpecifiedInTheRecipe() {
        val recipe = InstagramRecipe(videoField = "new_video")
        val media = InstagramParser.parse("""{"new_video":"https://cdn.example/video.mp4"}""", recipe = recipe)
        assertEquals("https://cdn.example/video.mp4", media.single().url)
    }

    @Test
    fun extractsAllItemsInMixedCarouselWithoutVideoThumbnails() {
        val html = """<script>{"edge_sidecar_to_children":{"edges":[{"node":{"is_video":false,"display_url":"https:\/\/cdn.example\/photo.jpg?x=1\u0026y=2"}},{"node":{"is_video":true,"display_url":"https:\/\/cdn.example\/cover.jpg","video_url":"https:\/\/cdn.example\/video.mp4"}}]}}</script>"""
        val media = InstagramParser.parse(html)
        assertEquals(listOf("https://cdn.example/photo.jpg?x=1&y=2", "https://cdn.example/video.mp4"), media.map { it.url })
        assertEquals(listOf(ResolvedMedia.Kind.IMAGE, ResolvedMedia.Kind.VIDEO), media.map { it.type })
    }

    @Test
    fun extractsDoubleEncodedVideoUrl() {
        val html = """<script>window.data = "{\"video_url\":\"https:\\/\\/cdn.example\\/video.mp4?a=1\\u0026b=2\"}";</script>"""
        val media = InstagramParser.parse(html)
        assertEquals("https://cdn.example/video.mp4?a=1&b=2", media.single().url)
        assertEquals(ResolvedMedia.Kind.VIDEO, media.single().type)
    }

    @Test
    fun usesOpenGraphImageWhenJsonIsAbsent() {
        val html = """<meta content="https://cdn.example/photo.jpg?a=1&amp;b=2" property="og:image">"""
        val media = InstagramParser.parse(html)
        assertEquals("https://cdn.example/photo.jpg?a=1&b=2", media.single().url)
    }

    @Test
    fun rejectsNonHttpMedia() {
        assertTrue(InstagramParser.parse("""{"video_url":"file:///data/private"}""").isEmpty())
    }

    @Test
    fun rejectsPrivateMediaAndUpgradesCleartextCdnLinksToHttps() {
        assertTrue(InstagramParser.parse("""{"video_url":"https://127.0.0.1/private.mp4"}""").isEmpty())
        assertEquals(
            "https://cdn.example/video.mp4",
            InstagramParser.parse("""{"video_url":"http://cdn.example/video.mp4"}""").single().url,
        )
    }

    @Test
    fun doesNotMistakeAReelCoverForTheVideo() {
        val html = "<meta property=\"og:image\" content=\"https://cdn.example/cover.png\">"
        assertTrue(InstagramParser.parse(html, requireVideo = true).isEmpty())
    }
}
