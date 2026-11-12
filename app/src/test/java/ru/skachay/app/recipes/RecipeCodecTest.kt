package ru.skachay.app.recipes

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class RecipeCodecTest {
    private fun defaults(): String = File("../recipe-defaults/recipes.json").readText()

    @Test
    fun readsTheBundledDefaultsAndCanDisableIndividualStrategies() {
        assertEquals(Recipes(), RecipeCodec.parse(defaults()))
        val json = JSONObject(defaults())
        json.getJSONObject("instagram").put("embed_enabled", false)
        val recipes = RecipeCodec.parse(json.toString())
        assertNotNull(recipes)
        assertFalse(recipes!!.instagram.embedEnabled)
        assertEquals("video_url", recipes.instagram.videoField)
        assertEquals(listOf("__UNIVERSAL_DATA_FOR_REHYDRATION__", "SIGI_STATE"), recipes.tiktok.scriptIds)
    }

    @Test
    fun rejectsUnexpectedSchemaAndUntrustedEndpoints() {
        assertNull(RecipeCodec.parse(JSONObject(defaults()).put("schema", 2).toString()))
        val json = JSONObject(defaults())
        json.getJSONObject("instagram").put("embed_template", "https://example.com/{shortcode}")
        assertNull(RecipeCodec.parse(json.toString()))
    }

    @Test
    fun rejectsInvalidPatternsAndUnsafeHeaders() {
        val brokenPattern = JSONObject(defaults())
        brokenPattern.getJSONObject("tiktok").put("post_pattern", "(")
        assertNull(RecipeCodec.parse(brokenPattern.toString()))
        val brokenHeader = JSONObject(defaults())
        brokenHeader.getJSONObject("instagram").put("mobile_agent", "agent\r\nInjected: value")
        assertNull(RecipeCodec.parse(brokenHeader.toString()))
        assertNull(RecipeCodec.parse("x".repeat(65_537)))
    }
}
