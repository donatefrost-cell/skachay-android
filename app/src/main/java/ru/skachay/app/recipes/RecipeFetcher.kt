package ru.skachay.app.recipes

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.skachay.app.network.readUtf8Bounded

internal data class RecipeDocument(val json: String, val recipes: Recipes)

internal class RecipeFetcher(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false).callTimeout(30, TimeUnit.SECONDS).build(),
    private val url: String = RAW_URL,
) {
    fun fetch(): RecipeDocument? = try {
        val request = Request.Builder().url(url).header("User-Agent", "Skachay/0.1 Android").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val json = response.body.readUtf8Bounded(MAX_BYTES) ?: return null
            val recipes = RecipeCodec.parse(json) ?: return null
            RecipeDocument(json, recipes)
        }
    } catch (_: IOException) {
        null
    }

    companion object {
        const val RAW_URL = "https://raw.githubusercontent.com/donatefrost-cell/skachay-android/main/recipe-defaults/recipes.json"
        private const val MAX_BYTES = 65_536
    }
}
