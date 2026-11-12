package ru.skachay.app.choice

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import ru.skachay.app.download.ResolvedMedia
import ru.skachay.app.network.UrlNetworkPolicy

internal data class SocialPreview(
    val id: String,
    val createdAt: Long,
    val items: List<ResolvedMedia>,
)

internal object SocialPreviewCodec {
    private const val MAX_TEXT = 1_000_000
    private const val MAX_ITEMS = 100
    private const val MAX_AGE_MS = 60 * 60 * 1000L
    private val allowedHeaders = setOf("User-Agent", "Referer")
    private val idPattern = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

    fun encode(
        source: String,
        id: String,
        createdAt: Long,
        items: List<ResolvedMedia>,
    ): String {
        val entries = JSONArray()
        items.forEach { item ->
            val headers = JSONObject()
            item.headers.filterKeys { it in allowedHeaders }.forEach { (key, value) -> headers.put(key, value) }
            entries.put(JSONObject().put("url", item.url).put("type", item.type.name).put("headers", headers))
        }
        return JSONObject()
            .put("source", source)
            .put("id", id)
            .put("created", createdAt)
            .put("items", entries)
            .toString()
    }

    fun decode(
        json: String,
        source: String,
        now: Long,
    ): SocialPreview? {
        if (json.length > MAX_TEXT) return null
        return try {
            val root = JSONObject(json)
            if (root.getString("source") != source) return null
            val id = root.getString("id").takeIf(idPattern::matches) ?: return null
            val created = root.getLong("created")
            if (created < 0 || created > now || now - created > MAX_AGE_MS) return null
            val array = root.getJSONArray("items")
            if (array.length() !in 1..MAX_ITEMS) return null
            val items =
                (0 until array.length()).map { index ->
                    val item = array.getJSONObject(index)
                    val url = item.getString("url")
                    val parsedUrl = if (url.length <= 8192) url.toHttpUrlOrNull() else null
                    if (parsedUrl == null || !UrlNetworkPolicy.isSafeHttpsUrl(parsedUrl)) return null
                    val kind =
                        try {
                            ResolvedMedia.Kind.valueOf(item.getString("type"))
                        } catch (_: IllegalArgumentException) {
                            return null
                        }
                    val headerObject = item.getJSONObject("headers")
                    if (headerObject.length() > allowedHeaders.size) return null
                    val headers = mutableMapOf<String, String>()
                    val keys = headerObject.keys()
                    while (keys.hasNext()) {
                        val name = keys.next()
                        val value = headerObject.getString(name)
                        if (name !in allowedHeaders || value.length > 16_384 || '\r' in value || '\n' in value) return null
                        headers[name] = value
                    }
                    ResolvedMedia(url, kind, headers)
                }
            SocialPreview(id, created, items)
        } catch (_: JSONException) {
            null
        }
    }
}

internal object SocialSelection {
    fun items(
        all: List<ResolvedMedia>,
        indexes: IntArray,
    ): List<ResolvedMedia>? {
        if (indexes.isEmpty() || indexes.size > all.size || indexes.toSet().size != indexes.size ||
            indexes.any { it !in all.indices }
        ) {
            return null
        }
        return indexes.sorted().map(all::get)
    }

    fun historyKey(
        base: String,
        indexes: IntArray,
        size: Int,
    ): String {
        require(indexes.isNotEmpty() && indexes.size <= size && indexes.toSet().size == indexes.size && indexes.all { it in 0 until size })
        return if (indexes.size == size) base else "$base::items:${indexes.sorted().joinToString(",")}"
    }
}
