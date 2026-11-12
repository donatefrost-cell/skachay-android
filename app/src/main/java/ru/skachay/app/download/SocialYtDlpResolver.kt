package ru.skachay.app.download

import android.content.Context
import ru.skachay.app.choice.AvailableFormat
import ru.skachay.app.choice.PreviewParser
import ru.skachay.app.choice.VideoPreview
import ru.skachay.app.recipes.RecipeRegistry

internal object SocialYtDlpResolver {
    fun resolve(context: Context, url: String): List<ResolvedMedia> {
        val recipes = RecipeRegistry.current
        if (!SocialFallbackPolicy.ytDlpEnabled(url, recipes)) return emptyList()
        val preview = PreviewParser.parse(YtDlpRuntime.extractPreview(context, url)) ?: return emptyList()
        return select(preview)
    }

    internal fun select(preview: VideoPreview): List<ResolvedMedia> = preview.formats.asSequence()
        .filter { it.hasVideo && it.hasAudio && it.directUrl != null }
        .maxWithOrNull(
            compareBy<AvailableFormat> { it.videoCodec?.startsWith("avc1", true) == true }
                .thenBy { it.extension == "mp4" }
                .thenBy { it.height ?: 0 }
                .thenBy { it.fps ?: 0 }
                .thenBy { it.videoBitrate ?: 0 },
        )
        ?.let { format ->
            format.directUrl?.let { directUrl ->
                listOf(ResolvedMedia(directUrl, ResolvedMedia.Kind.VIDEO, format.httpHeaders))
            }
        }
        .orEmpty()
}
