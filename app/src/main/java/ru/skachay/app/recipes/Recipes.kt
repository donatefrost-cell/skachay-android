package ru.skachay.app.recipes

private const val MOBILE = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36"

data class Recipes(
    val revision: Int = 1,
    val instagram: InstagramRecipe = InstagramRecipe(),
    val tiktok: TikTokRecipe = TikTokRecipe(),
)

data class InstagramRecipe(
    val embedEnabled: Boolean = true,
    val pageEnabled: Boolean = true,
    val ytDlpEnabled: Boolean = true,
    val embedTemplate: String = "https://www.instagram.com/p/{shortcode}/embed/captioned/",
    val pageTemplate: String = "https://www.instagram.com/p/{shortcode}/",
    val mobileAgent: String = MOBILE,
    val facebookAgent: String = "facebookexternalhit/1.1 (+https://www.facebook.com/externalhit_uatext.php)",
    val iosAgent: String =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 " +
            "Version/17.0 Mobile/15E148 Safari/604.1",
    val postPattern: String = "^/(?:[A-Za-z0-9._]+/)?(?:p|reel|reels|tv)/([A-Za-z0-9_-]+)/?$",
    val videoPattern: String = "^/(?:[A-Za-z0-9._]+/)?(?:reel|reels|tv)/[A-Za-z0-9_-]+/?$",
    val videoField: String = "video_url",
    val imageField: String = "display_url",
    val carouselField: String = "edge_sidecar_to_children",
    val videoMeta: List<String> = listOf("og:video:secure_url", "og:video", "twitter:player:stream"),
    val imageMeta: List<String> = listOf("og:image"),
)

data class TikTokRecipe(
    val pageEnabled: Boolean = true,
    val ytDlpEnabled: Boolean = true,
    val mobileAgent: String = MOBILE,
    val referer: String = "https://www.tiktok.com/",
    val postPattern: String = "/(?:video|photo)/(\\d+)(?:/|$)",
    val photoPattern: String = "/photo/\\d+(?:/|$)",
    val scriptIds: List<String> = listOf("__UNIVERSAL_DATA_FOR_REHYDRATION__", "SIGI_STATE"),
    val imagePostFields: List<String> = listOf("imagePost", "image_post"),
    val imageUrlFields: List<String> = listOf("imageURL", "displayImage", "image_url"),
    val videoUrlFields: List<String> = listOf("playAddr", "play_addr", "downloadAddr"),
    val nestedUrlFields: List<String> = listOf("urlList", "url_list", "UrlList", "urls", "url", "src"),
)

object RecipeRegistry {
    @Volatile var current: Recipes = Recipes()
        internal set
}
