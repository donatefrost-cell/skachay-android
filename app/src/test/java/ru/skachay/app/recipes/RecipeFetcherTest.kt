package ru.skachay.app.recipes

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeFetcherTest {
    @Test
    fun acceptsValidatedDataAndRejectsInvalidOrOversizedResponses() {
        val good = File("../recipe-defaults/recipes.json").readText()
        val invalid = "{\"schema\":2}"
        val oversized = "x".repeat(65_537)

        assertEquals(Recipes(), RecipeFetcher { good }.fetch()?.recipes)
        assertNull(RecipeFetcher { invalid }.fetch())
        assertNull(RecipeFetcher { oversized }.fetch())
        assertNull(RecipeFetcher { null }.fetch())
    }

    @Test
    fun remoteEndpointIsPinnedToTheOfficialHttpsRecipeFile() {
        assertTrue(RecipeFetcher.isAllowedEndpoint(RecipeFetcher.RAW_URL))
        assertFalse(RecipeFetcher.isAllowedEndpoint(RecipeFetcher.RAW_URL.replace("https://", "http://")))
        assertFalse(RecipeFetcher.isAllowedEndpoint(RecipeFetcher.RAW_URL.replace("raw.githubusercontent.com", "raw.githubusercontent.com.evil.example")))
        assertFalse(RecipeFetcher.isAllowedEndpoint("https://user@raw.githubusercontent.com/donatefrost-cell/skachay-android/main/recipe-defaults/recipes.json"))
        assertFalse(RecipeFetcher.isAllowedEndpoint(RecipeFetcher.RAW_URL + "?redirect=https://example.com"))
        assertFalse(RecipeFetcher.isAllowedEndpoint(RecipeFetcher.RAW_URL.replace("recipe-defaults/recipes.json", "evil.json")))
    }
}
