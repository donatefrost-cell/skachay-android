package ru.skachay.app.recipes

import com.google.re2j.Pattern
import com.google.re2j.PatternSyntaxException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.URI
import java.net.URISyntaxException

object RecipeCodec {
    private const val MAX_BYTES = 65_536
    private val identifier = Regex("[A-Za-z_][A-Za-z0-9_]{0,63}")
    private val metaName = Regex("[A-Za-z0-9_:-]{1,80}")

    fun parse(raw: String): Recipes? {
        if (raw.toByteArray(Charsets.UTF_8).size > MAX_BYTES) return null
        return try {
            val root = JSONObject(raw)
            require((root.get("schema") as? Number)?.toDouble() == 1.0)
            val revision = root.get("revision") as? Number ?: throw IllegalArgumentException("revision")
            require(revision.toDouble() == revision.toInt().toDouble() && revision.toInt() in 1..2_000_000_000)
            val ig = root.getJSONObject("instagram")
            val tt = root.getJSONObject("tiktok")
            Recipes(
                revision = revision.toInt(),
                instagram =
                    InstagramRecipe(
                        embedEnabled = ig.bool("embed_enabled"),
                        pageEnabled = ig.bool("page_enabled"),
                        ytDlpEnabled = ig.bool("ytdlp_enabled"),
                        embedTemplate = ig.template("embed_template"),
                        pageTemplate = ig.template("page_template"),
                        mobileAgent = ig.agent("mobile_agent"),
                        facebookAgent = ig.agent("facebook_agent"),
                        iosAgent = ig.agent("ios_agent"),
                        postPattern = ig.pattern("post_pattern", capture = true),
                        videoPattern = ig.pattern("video_pattern"),
                        videoField = ig.field("video_field"),
                        imageField = ig.field("image_field"),
                        carouselField = ig.field("carousel_field"),
                        videoMeta = ig.fields("video_meta", metaName),
                        imageMeta = ig.fields("image_meta", metaName),
                    ),
                tiktok =
                    TikTokRecipe(
                        pageEnabled = tt.bool("page_enabled"),
                        ytDlpEnabled = tt.bool("ytdlp_enabled"),
                        mobileAgent = tt.agent("mobile_agent"),
                        referer = tt.url("referer", "www.tiktok.com"),
                        postPattern = tt.pattern("post_pattern", capture = true),
                        photoPattern = tt.pattern("photo_pattern"),
                        scriptIds = tt.fields("script_ids", identifier),
                        imagePostFields = tt.fields("image_post_fields", identifier),
                        imageUrlFields = tt.fields("image_url_fields", identifier),
                        videoUrlFields = tt.fields("video_url_fields", identifier),
                        nestedUrlFields = tt.fields("nested_url_fields", identifier),
                    ),
            )
        } catch (_: JSONException) {
            null
        } catch (_: PatternSyntaxException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun JSONObject.bool(key: String): Boolean = get(key) as? Boolean ?: throw IllegalArgumentException(key)

    private fun JSONObject.string(
        key: String,
        max: Int,
    ): String {
        val value = get(key) as? String ?: throw IllegalArgumentException(key)
        require(value.isNotEmpty() && value.length <= max && value.all { it.code in 32..126 })
        return value
    }

    private fun JSONObject.agent(key: String): String = string(key, 512)

    private fun JSONObject.field(key: String): String = string(key, 64).also { require(identifier.matches(it)) }

    private fun JSONObject.fields(
        key: String,
        allowed: Regex,
    ): List<String> {
        val array = get(key) as? JSONArray ?: throw IllegalArgumentException(key)
        require(array.length() in 1..16)
        return (0 until array.length())
            .map { index ->
                val value = array.get(index) as? String ?: throw IllegalArgumentException(key)
                require(allowed.matches(value))
                value
            }.also { require(it.distinct().size == it.size) }
    }

    private fun JSONObject.pattern(
        key: String,
        capture: Boolean = false,
    ): String {
        val value = string(key, 256)
        val compiled = Pattern.compile(value)
        if (capture) require(compiled.matcher("").groupCount() == 1)
        return value
    }

    private fun JSONObject.template(key: String): String {
        val value = string(key, 512)
        require(value.indexOf("{shortcode}") >= 0 && value.indexOf("{shortcode}") == value.lastIndexOf("{shortcode}"))
        validateUrl(value.replace("{shortcode}", "example"), "www.instagram.com")
        return value
    }

    private fun JSONObject.url(
        key: String,
        host: String,
    ): String = string(key, 512).also { validateUrl(it, host) }

    private fun validateUrl(
        value: String,
        host: String,
    ) {
        val uri =
            try {
                URI(value)
            } catch (_: URISyntaxException) {
                throw IllegalArgumentException("URL")
            }
        require(uri.scheme == "https" && uri.host == host && uri.userInfo == null && uri.port in listOf(-1, 443))
        require(uri.fragment == null && uri.query == null)
    }
}
