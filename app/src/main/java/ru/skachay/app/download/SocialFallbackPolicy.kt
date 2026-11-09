package ru.skachay.app.download

import ru.skachay.app.recipes.Recipes

internal object SocialFallbackPolicy {
    fun ytDlpEnabled(url: String, recipes: Recipes): Boolean = when {
        InstagramLink.supports(url) -> recipes.instagram.ytDlpEnabled
        TikTokLink.supports(url) -> recipes.tiktok.ytDlpEnabled
        else -> false
    }
}
