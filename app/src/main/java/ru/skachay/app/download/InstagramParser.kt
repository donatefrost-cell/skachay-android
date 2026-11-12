package ru.skachay.app.download

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONException
import org.json.JSONObject
import ru.skachay.app.network.UrlNetworkPolicy
import ru.skachay.app.recipes.InstagramRecipe
import ru.skachay.app.recipes.RecipeRegistry

object InstagramParser {
    fun parse(
        html: String,
        requireVideo: Boolean = false,
        recipe: InstagramRecipe = RecipeRegistry.current.instagram,
    ): List<ResolvedMedia> {
        val items = parseContent(html, recipe)
        return if (requireVideo && items.none { it.type == ResolvedMedia.Kind.VIDEO }) emptyList() else items
    }

    private fun parseContent(
        html: String,
        recipe: InstagramRecipe,
    ): List<ResolvedMedia> {
        val layers = mutableListOf(html.replace("&amp;", "&").replace("&quot;", "\""))
        repeat(3) { layers.add(unescapeLayer(layers.last())) }
        for (layer in layers) {
            val carousel = carousel(layer, recipe)
            if (carousel != null) return carousel
        }
        for (layer in layers) {
            field(layer, recipe.videoField)?.let { media(it, ResolvedMedia.Kind.VIDEO) }?.let { return listOf(it) }
        }
        for (layer in layers) {
            field(layer, recipe.imageField)?.let { media(it, ResolvedMedia.Kind.IMAGE) }?.let { return listOf(it) }
        }
        return openGraph(layers.first(), recipe)
    }

    private fun carousel(
        text: String,
        recipe: InstagramRecipe,
    ): List<ResolvedMedia>? {
        val marker = Regex("\"${recipe.carouselField}\"\\s*:\\s*").find(text) ?: return null
        val start = marker.range.last + 1
        if (start >= text.length || text[start] != '{') return null
        val json = balancedObject(text, start) ?: return null
        val objectValue =
            try {
                JSONObject(json)
            } catch (_: JSONException) {
                return null
            }
        val edges = objectValue.optJSONArray("edges") ?: return null
        val result = ArrayList<ResolvedMedia>()
        for (index in 0 until edges.length()) {
            val edge = edges.optJSONObject(index) ?: return emptyList()
            val node = edge.optJSONObject("node") ?: return emptyList()
            val video = node.optBoolean("is_video") || node.optString("__typename") == "GraphVideo"
            val kind = if (video) ResolvedMedia.Kind.VIDEO else ResolvedMedia.Kind.IMAGE
            val key = if (video) recipe.videoField else recipe.imageField
            val item = media(node.optString(key), kind) ?: return emptyList()
            result.add(item)
        }
        return result.takeIf { it.isNotEmpty() }
    }

    private fun balancedObject(
        text: String,
        start: Int,
    ): String? {
        var depth = 0
        var quoted = false
        var escaped = false
        for (index in start until text.length) {
            val char = text[index]
            if (quoted) {
                if (escaped) {
                    escaped = false
                } else if (char == '\\') {
                    escaped = true
                } else if (char == '"') {
                    quoted = false
                }
            } else {
                if (char == '"') {
                    quoted = true
                } else if (char == '{') {
                    depth++
                } else if (char == '}' && --depth == 0) {
                    return text.substring(start, index + 1)
                }
            }
        }
        return null
    }

    private fun field(
        text: String,
        key: String,
    ): String? {
        val match = Regex("\"$key\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").find(text) ?: return null
        return try {
            JSONObject("{\"value\":\"${match.groupValues[1]}\"}").getString("value")
        } catch (_: JSONException) {
            null
        }
    }

    private fun openGraph(
        html: String,
        recipe: InstagramRecipe,
    ): List<ResolvedMedia> {
        val attributes = Regex("([\\w:-]+)\\s*=\\s*([\"'])(.*?)\\2", RegexOption.DOT_MATCHES_ALL)
        val values = mutableMapOf<String, String>()
        Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(html).forEach { tag ->
            val attrs = attributes.findAll(tag.value).associate { it.groupValues[1].lowercase() to it.groupValues[3] }
            val key = attrs["property"] ?: attrs["name"]
            if (key != null && attrs["content"] != null) values[key.lowercase()] = attrs.getValue("content")
        }
        for (key in recipe.videoMeta) {
            values[key]?.let { media(it, ResolvedMedia.Kind.VIDEO) }?.let { return listOf(it) }
        }
        for (key in recipe.imageMeta) {
            values[key]?.let { media(it, ResolvedMedia.Kind.IMAGE) }?.let { return listOf(it) }
        }
        return emptyList()
    }

    private fun media(
        value: String,
        kind: ResolvedMedia.Kind,
    ): ResolvedMedia? {
        val decoded = value.replace("&amp;", "&").replace("&#38;", "&")
        if (decoded.length > MAX_MEDIA_URL_LENGTH) return null
        val url = decoded.replaceFirst(Regex("^http://", RegexOption.IGNORE_CASE), "https://")
        val parsed = url.toHttpUrlOrNull() ?: return null
        if (!UrlNetworkPolicy.isSafeHttpsUrl(parsed)) return null
        return ResolvedMedia(url, kind)
    }

    private const val MAX_MEDIA_URL_LENGTH = 8192

    private fun unescapeLayer(text: String): String =
        buildString {
            var index = 0
            while (index < text.length) {
                if (text[index] != '\\' || index + 1 >= text.length) {
                    append(text[index++])
                    continue
                }
                val next = text[index + 1]
                if (next == 'u' && index + 6 <= text.length) {
                    val code = text.substring(index + 2, index + 6).toIntOrNull(16)
                    if (code != null) {
                        append(code.toChar())
                        index += 6
                        continue
                    }
                }
                if (next == '\\' || next == '/' || next == '"') {
                    append(next)
                    index += 2
                } else {
                    append(text[index++])
                }
            }
        }
}
