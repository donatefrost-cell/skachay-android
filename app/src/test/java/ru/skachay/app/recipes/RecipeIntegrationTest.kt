package ru.skachay.app.recipes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.skachay.app.download.InstagramLink
import ru.skachay.app.download.InstagramResolver
import ru.skachay.app.download.TikTokLink
import ru.skachay.app.download.TikTokResolver

class RecipeIntegrationTest {
    @Test
    fun appliesUpdatedLinkPatternsAndDisabledStrategies() {
        val previous = RecipeRegistry.current
        try {
            RecipeRegistry.current =
                Recipes(
                    instagram =
                        InstagramRecipe(
                            postPattern = "^/article/([A-Za-z0-9_-]+)/?$",
                            embedEnabled = false,
                            pageEnabled = false,
                        ),
                    tiktok = TikTokRecipe(postPattern = "/new/(\\d+)$", pageEnabled = false),
                )
            assertEquals("abc123", InstagramLink.shortcode("https://instagram.com/article/abc123/"))
            assertEquals("456", TikTokLink.postId("https://www.tiktok.com/new/456"))
            assertTrue(InstagramResolver().resolve("https://instagram.com/article/abc123/").isEmpty())
            assertTrue(TikTokResolver().resolve("https://www.tiktok.com/new/456").isEmpty())
        } finally {
            RecipeRegistry.current = previous
        }
    }
}
