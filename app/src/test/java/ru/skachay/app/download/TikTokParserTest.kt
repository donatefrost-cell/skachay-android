package ru.skachay.app.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.skachay.app.recipes.TikTokRecipe

class TikTokParserTest {
    @Test
    fun readsScriptAndVideoFieldSpecifiedInTheRecipe() {
        val recipe = TikTokRecipe(scriptIds = listOf("NEW_STATE"), videoUrlFields = listOf("newAddress"))
        val html = """<script id="NEW_STATE">{"item":{"id":"123","video":{"newAddress":"https://cdn.example/video.mp4"}}}</script>"""
        assertEquals("https://cdn.example/video.mp4", TikTokParser.parse(html, "123", recipe).single().url)
    }

    @Test
    fun readsVideoForTheRequestedPost() {
        val html = """<script id="__UNIVERSAL_DATA_FOR_REHYDRATION__" type="application/json">{"data":{"itemStruct":{"id":"123","video":{"playAddr":"https://cdn.example/no-watermark.mp4","downloadAddr":"https://cdn.example/watermark.mp4"}}}}</script>"""
        val media = TikTokParser.parse(html, "123")
        assertEquals("https://cdn.example/no-watermark.mp4", media.single().url)
        assertEquals(ResolvedMedia.Kind.VIDEO, media.single().type)
    }

    @Test
    fun readsAllSlideshowPhotosInOrder() {
        val html = """<script id="SIGI_STATE">{"ItemModule":{"456":{"id":"456","video":{"playAddr":"https://cdn.example/music.mp4"},"imagePost":{"images":[{"imageURL":{"urlList":["https://cdn.example/1.jpg"]}},{"displayImage":{"urlList":["https://cdn.example/2.jpg"]}}]}}}}</script>"""
        val media = TikTokParser.parse(html, "456")
        assertEquals(listOf("https://cdn.example/1.jpg", "https://cdn.example/2.jpg"), media.map { it.url })
        assertTrue(media.all { it.type == ResolvedMedia.Kind.IMAGE })
    }

    @Test
    fun ignoresRecommendationsAndAvatarsForAnotherPost() {
        val html = """<script id="SIGI_STATE">{"ItemModule":{"999":{"id":"999","video":{"playAddr":"https://cdn.example/other.mp4"}}},"avatar":"https://cdn.example/avatar.jpg"}</script>"""
        assertTrue(TikTokParser.parse(html, "123").isEmpty())
    }

    @Test
    fun rejectsIncompleteSlideshowsInsteadOfReturningOnlySomePhotos() {
        val html = """<script id="SIGI_STATE">{"itemStruct":{"id":"456","imagePost":{"images":[{"imageURL":{"urlList":["https://cdn.example/1.jpg"]}},{"imageURL":{}}]}}}</script>"""
        assertTrue(TikTokParser.parse(html, "456").isEmpty())
    }

    @Test
    fun rejectsPrivateAddressesAndUpgradesMediaLinksToHttps() {
        val private = """<script id="SIGI_STATE">{"itemStruct":{"id":"456","video":{"playAddr":"https://127.0.0.1/private.mp4"}}}</script>"""
        val cleartext = """<script id="SIGI_STATE">{"itemStruct":{"id":"456","video":{"playAddr":"http://cdn.example/video.mp4"}}}</script>"""
        assertTrue(TikTokParser.parse(private, "456").isEmpty())
        assertEquals("https://cdn.example/video.mp4", TikTokParser.parse(cleartext, "456").single().url)
    }
}
