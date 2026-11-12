package ru.skachay.app.recipes

import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import ru.skachay.app.network.UrlNetworkPolicy
import ru.skachay.app.network.readUtf8Bounded

internal data class RecipeDocument(val json: String, val recipes: Recipes)

internal fun interface RecipeSource {
    fun read(): String?
}

internal class RecipeFetcher(private val source: RecipeSource = GithubRecipeSource()) {
    fun fetch(): RecipeDocument? {
        val json = try {
            source.read() ?: return null
        } catch (_: IOException) {
            return null
        }
        val recipes = RecipeCodec.parse(json) ?: return null
        return RecipeDocument(json, recipes)
    }

    companion object {
        const val RAW_URL = "https://raw.githubusercontent.com/donatefrost-cell/skachay-android/main/recipe-defaults/recipes.json"
        internal const val MAX_BYTES = 65_536
        private const val ALLOWED_HOST = "raw.githubusercontent.com"
        private const val ALLOWED_PATH = "/donatefrost-cell/skachay-android/main/recipe-defaults/recipes.json"

        internal fun isAllowedEndpoint(value: String): Boolean {
            val endpoint = value.toHttpUrlOrNull() ?: return false
            return isAllowedEndpoint(endpoint)
        }

        private fun isAllowedEndpoint(endpoint: HttpUrl): Boolean =
            endpoint.host == ALLOWED_HOST &&
                endpoint.encodedPath == ALLOWED_PATH &&
                endpoint.query == null &&
                endpoint.fragment == null &&
                endpoint.username.isEmpty() &&
                endpoint.password.isEmpty() &&
                UrlNetworkPolicy.isSafeHttpsUrl(endpoint)
    }
}

private class GithubRecipeSource : RecipeSource {
    private val client = UrlNetworkPolicy.publicHttpsClient(
        callTimeoutSeconds = 30,
        allowedRedirectHostSuffixes = setOf("raw.githubusercontent.com"),
    )

    override fun read(): String? {
        if (!RecipeFetcher.isAllowedEndpoint(RecipeFetcher.RAW_URL)) return null
        val request = Request.Builder().url(RecipeFetcher.RAW_URL)
            .header("User-Agent", "Skachay/0.1 Android")
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body.readUtf8Bounded(RecipeFetcher.MAX_BYTES)
        }
    }
}
