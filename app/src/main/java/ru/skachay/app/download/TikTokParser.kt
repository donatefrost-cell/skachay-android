package ru.skachay.app.download

import java.net.URI
import java.net.URISyntaxException
import java.util.ArrayDeque
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import ru.skachay.app.recipes.RecipeRegistry
import ru.skachay.app.recipes.TikTokRecipe

object TikTokParser {
    fun parse(html: String, id: String, recipe: TikTokRecipe = RecipeRegistry.current.tiktok): List<ResolvedMedia> {
        val scripts = Regex("<script\\b([^>]*)>(.*?)</script\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val idAttribute = Regex("\\bid\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
        for (script in scripts.findAll(html)) {
            val scriptId = idAttribute.find(script.groupValues[1])?.groupValues?.get(1)
            if (scriptId !in recipe.scriptIds) continue
            val root = try { JSONTokener(script.groupValues[2]).nextValue() } catch (_: JSONException) { continue }
            val item = findItem(root, id, recipe) ?: continue
            val imagePost = recipe.imagePostFields.firstNotNullOfOrNull(item::optJSONObject)
            if (imagePost != null) {
                val images = imagePost.optJSONArray("images") ?: return emptyList()
                val result = ArrayList<ResolvedMedia>()
                for (index in 0 until images.length()) {
                    val image = images.optJSONObject(index) ?: return emptyList()
                    val url = recipe.imageUrlFields.firstNotNullOfOrNull { urlOf(image.opt(it), recipe) } ?: return emptyList()
                    result.add(ResolvedMedia(url, ResolvedMedia.Kind.IMAGE))
                }
                return result
            }
            val video = item.optJSONObject("video") ?: continue
            val address = recipe.videoUrlFields.firstNotNullOfOrNull { urlOf(video.opt(it), recipe) } ?: continue
            return listOf(ResolvedMedia(address, ResolvedMedia.Kind.VIDEO))
        }
        return emptyList()
    }

    private fun findItem(root: Any, id: String, recipe: TikTokRecipe): JSONObject? {
        val queue = ArrayDeque<Any>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited++ < 50_000) {
            when (val current = queue.removeFirst()) {
                is JSONObject -> {
                    if (current.optString("id") == id && (current.has("video") || recipe.imagePostFields.any(current::has))) return current
                    val keys = current.keys()
                    while (keys.hasNext()) {
                        val value = current.opt(keys.next())
                        if (value is JSONObject || value is JSONArray) queue.add(value)
                    }
                }
                is JSONArray -> for (index in 0 until current.length()) {
                    val value = current.opt(index)
                    if (value is JSONObject || value is JSONArray) queue.add(value)
                }
            }
        }
        return null
    }

    private fun urlOf(value: Any?, recipe: TikTokRecipe): String? = when (value) {
        is String -> {
            val uri = try { URI(value) } catch (_: URISyntaxException) { null }
            value.takeIf { uri?.scheme in listOf("http", "https") && uri?.host != null && uri.userInfo == null }
        }
        is JSONArray -> (0 until value.length()).firstNotNullOfOrNull { urlOf(value.opt(it), recipe) }
        is JSONObject -> recipe.nestedUrlFields.firstNotNullOfOrNull { urlOf(value.opt(it), recipe) }
        else -> null
    }
}
