package ru.skachay.app.recipes

import android.content.Context
import java.io.IOException

internal object RecipeStore {
    fun load(context: Context) {
        val bundled =
            try {
                context.assets
                    .open("recipes.json")
                    .bufferedReader()
                    .use { RecipeCodec.parse(it.readText()) }
            } catch (_: IOException) {
                null
            } ?: Recipes()
        val saved =
            context
                .getSharedPreferences("recipes", Context.MODE_PRIVATE)
                .getString("data", null)
                ?.let(RecipeCodec::parse)
        RecipeRegistry.current = saved?.takeIf { it.revision >= bundled.revision } ?: bundled
    }

    fun refresh(context: Context) {
        val document = RecipeFetcher().fetch() ?: return
        if (document.recipes.revision < RecipeRegistry.current.revision) return
        val saved =
            context
                .getSharedPreferences("recipes", Context.MODE_PRIVATE)
                .edit()
                .putString("data", document.json)
                .commit()
        if (saved) RecipeRegistry.current = document.recipes
    }
}
